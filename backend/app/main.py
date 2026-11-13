
"""Stateless HTTPS adapter for Librus Synergia.
Supabase access tokens are verified against the Supabase Auth server.
Librus credentials and session cookies are never stored on the server.
"""
from __future__ import annotations

import asyncio
import os
import secrets
import time
import urllib.error
import urllib.request
import json

from collections import defaultdict, deque
from dataclasses import asdict
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from pydantic import BaseModel, ConfigDict, Field, SecretStr

from librus_synergia import (
    Librus,
    LibrusAccountActionRequiredError,
    LibrusAuthError,
    LibrusCaptchaRequiredError,
    LibrusConnectionError,
    LibrusInvalidCredentialsError,
    LibrusMessageSendError,
    LibrusSessionData,
)

app = FastAPI(title="Librus All-in-One API", version="0.3.0")

_hits: dict[str, deque[float]] = defaultdict(deque)
_LIMIT = 10
_WINDOW = 60.0


@app.middleware("http")
async def protect_and_no_store(request: Request, call_next):
    response = await call_next(request)
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"
    response.headers["X-Content-Type-Options"] = "nosniff"
    return response


def check_rate_limit(request: Request) -> None:
    ip = request.client.host if request.client else "unknown"
    now = time.monotonic()
    hits = _hits[ip]

    while hits and now - hits[0] > _WINDOW:
        hits.popleft()

    if len(hits) >= _LIMIT:
        raise HTTPException(
            429,
            "Za dużo żądań. Spróbuj ponownie później."
        )

    hits.append(now)


def verify_supabase_token(token: str) -> dict[str, Any]:
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    publishable_key = os.environ.get(
        "SUPABASE_PUBLISHABLE_KEY", ""
    )

    if not supabase_url or not publishable_key:
        raise HTTPException(
            503,
            "Brak konfiguracji Supabase na serwerze."
        )

    url = supabase_url + "/auth/v1/user"

    request = urllib.request.Request(
        url,
        headers={
            "apikey": publishable_key,
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
        },
        method="GET",
    )

    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            user = json.loads(response.read().decode("utf-8"))

        if not isinstance(user, dict) or not user.get("id"):
            raise HTTPException(
                401,
                "Supabase nie potwierdził użytkownika."
            )

        return user

    except urllib.error.HTTPError as error:
        if error.code in (400, 401, 403):
            raise HTTPException(
                401,
                "Sesja użytkownika jest nieważna lub wygasła."
            ) from None

        raise HTTPException(
            503,
            "Nie udało się zweryfikować sesji Supabase."
        ) from None

    except (urllib.error.URLError, TimeoutError, OSError):
        raise HTTPException(
            503,
            "Serwer Supabase jest chwilowo niedostępny."
        ) from None

    except json.JSONDecodeError:
        raise HTTPException(
            502,
            "Nieprawidłowa odpowiedź serwera Supabase."
        ) from None


async def require_user(
    authorization: str | None,
) -> dict[str, Any]:
    if not authorization:
        raise HTTPException(
            401,
            "Brak tokenu logowania."
        )

    parts = authorization.split(" ", 1)

    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise HTTPException(
            401,
            "Nieprawidłowy format tokenu."
        )

    token = parts[1].strip()

    if not token or len(token) > 10000:
        raise HTTPException(
            401,
            "Nieprawidłowy token."
        )

    return await asyncio.to_thread(
        verify_supabase_token,
        token
    )


class SessionCookie(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name: str = Field(min_length=1, max_length=64)
    value: str = Field(min_length=1, max_length=8192)
    domain: str = Field(min_length=1, max_length=128)
    path: str | None = Field(default=None, max_length=256)


class SessionState(BaseModel):
    model_config = ConfigDict(extra="ignore")

    cookies: list[SessionCookie] = Field(
        default_factory=list,
        max_length=32
    )
    logged_in_at: float = 0.0


class Credentials(BaseModel):
    model_config = ConfigDict(extra="forbid")

    profile: str = Field(pattern="^(parent|student)$")
    login: str = Field(min_length=1, max_length=128)
    password: SecretStr = Field(min_length=1, max_length=512)
    session: SessionState | None = None


def _session_data(
    value: SessionState | None
) -> LibrusSessionData | None:
    if value is None:
        return None

    return LibrusSessionData(
        cookies=[
            cookie.model_dump(exclude_none=True)
            for cookie in value.cookies
        ],
        logged_in_at=value.logged_in_at,
    )


def _safe_error(error: Exception) -> HTTPException:
    if isinstance(error, LibrusCaptchaRequiredError):
        return HTTPException(
            428,
            "Librus wymaga dodatkowej weryfikacji. "
            "Zaloguj się w oficjalnym portalu."
        )

    if isinstance(error, LibrusInvalidCredentialsError):
        return HTTPException(
            401,
            "Librus odrzucił login lub hasło."
        )

    if isinstance(error, LibrusAccountActionRequiredError):
        return HTTPException(
            409,
            "Librus wymaga działania na koncie. "
            "Zaloguj się w oficjalnym portalu."
        )

    if isinstance(error, LibrusAuthError):
        return HTTPException(
            401,
            "Nie udało się uwierzytelnić w Librusie."
        )

    if isinstance(error, LibrusConnectionError):
        return HTTPException(
            503,
            "Librus jest chwilowo niedostępny. Spróbuj ponownie."
        )

    return HTTPException(
        502,
        "Błąd komunikacji z Librusem."
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "librus-all-in-one-api"
    }


@app.get("/v1/session-check")
async def session_check(
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user = await require_user(authorization)
    check_rate_limit(request)

    return {
        "ok": True,
        "authenticated": True,
        "user_id": user["id"],
        "email": user.get("email"),
    }


@app.post("/v1/connect")
async def connect(
    payload: Credentials,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            me = await librus.me()
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "student": asdict(me),
            "session": state,
        })

    except Exception as error:
        raise _safe_error(error) from None


class MessageRequest(Credentials):
    message_id: str = Field(min_length=1, max_length=128)
    mailbox: str = Field(default="inbox", pattern="^(inbox|outbox|archive/inbox|archive/outbox)$")


class MessageSendRequest(Credentials):
    recipient_ids: list[str] = Field(min_length=1, max_length=10)
    topic: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=10000)


@app.post("/v1/message/recipients")
async def get_message_recipients(
    payload: Credentials,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Lista odbiorców wiadomości jest dostępna wyłącznie z konta rodzica.")

    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            recipients = await librus.message_recipients()
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "recipients": [asdict(item) for item in recipients],
            "session": state,
        })

    except Exception as error:
        raise _safe_error(error) from None


@app.post("/v1/message/send")
async def send_message(
    payload: MessageSendRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Wysyłanie wiadomości jest dostępne wyłącznie z konta rodzica.")

    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            available_ids = {item.account_id for item in await librus.message_recipients()}
            if any(recipient_id not in available_ids for recipient_id in payload.recipient_ids):
                raise HTTPException(422, "Odbiorca nie znajduje się na aktualnej liście odbiorców Librusa.")

            sent = await librus.send_message(
                payload.recipient_ids,
                payload.topic,
                payload.text,
            )
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "sent": asdict(sent),
            "session": state,
        })

    except HTTPException:
        raise
    except LibrusMessageSendError as error:
        # Librus sends exactly once. Unknown outcomes must never be retried
        # automatically; the parent should check the Sent mailbox first.
        if error.sent is None:
            raise HTTPException(
                502,
                {
                    "message": "Librus nie potwierdził, czy wiadomość została wysłana. Sprawdź folder Wysłane przed ponowną próbą.",
                    "sent": None,
                },
            ) from None
        raise HTTPException(
            400,
            {
                "message": "Librus nie wysłał wiadomości.",
                "sent": False,
            },
        ) from None
    except ValueError as error:
        raise HTTPException(422, str(error)) from None
    except Exception as error:
        raise _safe_error(error) from None


@app.post("/v1/message")
async def get_message(
    payload: MessageRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            message = await librus.message(payload.message_id, payload.mailbox)
            state = asdict(librus.session_data)

        if message is None:
            raise HTTPException(404, "Nie znaleziono wiadomości w Librusie.")

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "message": asdict(message),
            "session": state,
        })

    except HTTPException:
        raise
    except Exception as error:
        raise _safe_error(error) from None


@app.post("/v1/sync")
async def sync(
    payload: Credentials,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            data = await librus.fetch_all()
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "data": asdict(data),
            "session": state,
        })

    except Exception as error:
        raise _safe_error(error) from None
