
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
