"""Stateless HTTPS adapter for Librus Synergia.
Supabase access tokens are verified against the Supabase Auth server.
Librus credentials and session cookies are never stored on the server.
"""
from __future__ import annotations

import base64
import asyncio
import os
import secrets
import time
import urllib.error
import urllib.request
import json

from collections import defaultdict, deque
from dataclasses import asdict
from datetime import date
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from pydantic import BaseModel, ConfigDict, Field, SecretStr

from librus_synergia.const import USER_AGENT
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

    # Bound memory use when many distinct client IPs have contacted this
    # single-process limiter over time.
    if len(_hits) > 2048:
        stale_ips = [
            key for key, values in _hits.items()
            if not values or now - values[-1] > _WINDOW
        ]
        for key in stale_ips:
            _hits.pop(key, None)

    hits = _hits[ip]

    while hits and now - hits[0] > _WINDOW:
        hits.popleft()

    if len(hits) >= _LIMIT:
        raise HTTPException(
            429,
            "Za dużo żądań. Spróbuj ponownie później."
        )

    hits.append(now)


def _api_error(status_code: int, code: str, detail: str) -> HTTPException:
    """Attach a stable machine-readable code without changing the response body shape."""
    return HTTPException(
        status_code=status_code,
        detail=detail,
        headers={"X-Librus-Error-Code": code},
    )


def verify_supabase_token(token: str) -> dict[str, Any]:
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    publishable_key = os.environ.get(
        "SUPABASE_PUBLISHABLE_KEY", ""
    )

    if not supabase_url or not publishable_key:
        raise _api_error(503, "SUPABASE_NOT_CONFIGURED", "Brak konfiguracji Supabase na serwerze.")

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
            raise _api_error(401, "SUPABASE_SESSION_INVALID", "Supabase nie potwierdził użytkownika.")

        return user

    except urllib.error.HTTPError as error:
        if error.code in (400, 401, 403):
            raise _api_error(401, "SUPABASE_SESSION_EXPIRED", "Sesja użytkownika jest nieważna lub wygasła.") from None

        raise _api_error(503, "SUPABASE_UNAVAILABLE", "Nie udało się zweryfikować sesji Supabase.") from None

    except (urllib.error.URLError, TimeoutError, OSError):
        raise _api_error(503, "SUPABASE_UNAVAILABLE", "Serwer Supabase jest chwilowo niedostępny.") from None

    except json.JSONDecodeError:
        raise _api_error(502, "SUPABASE_BAD_RESPONSE", "Nieprawidłowa odpowiedź serwera Supabase.") from None


async def require_user(
    authorization: str | None,
) -> dict[str, Any]:
    if not authorization:
        raise _api_error(401, "AUTH_TOKEN_MISSING", "Brak tokenu logowania.")

    parts = authorization.split(" ", 1)

    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise _api_error(401, "AUTH_TOKEN_INVALID", "Nieprawidłowy format tokenu.")

    token = parts[1].strip()

    if not token or len(token) > 10000:
        raise _api_error(401, "AUTH_TOKEN_INVALID", "Nieprawidłowy token.")

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

class SyncRequest(Credentials):
    # Background notification checks need only changed-data endpoints and
    # the grade types not included by fetch_changes().
    notifications_only: bool = False


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
        return _api_error(428, "LIBRUS_CAPTCHA_REQUIRED", "Librus wymaga dodatkowej weryfikacji. Zaloguj się w oficjalnym portalu.")

    if isinstance(error, LibrusInvalidCredentialsError):
        return _api_error(401, "LIBRUS_INVALID_CREDENTIALS", "Librus odrzucił login lub hasło.")

    if isinstance(error, LibrusAccountActionRequiredError):
        return _api_error(409, "LIBRUS_ACCOUNT_ACTION_REQUIRED", "Librus wymaga działania na koncie. Zaloguj się w oficjalnym portalu.")

    if isinstance(error, LibrusAuthError):
        return _api_error(401, "LIBRUS_AUTH_FAILED", "Nie udało się uwierzytelnić w Librusie.")

    if isinstance(error, LibrusConnectionError):
        return _api_error(503, "LIBRUS_UNAVAILABLE", "Librus jest chwilowo niedostępny. Spróbuj ponownie.")

    return _api_error(502, "LIBRUS_API_ERROR", "Błąd komunikacji z Librusem.")


@app.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "librus-all-in-one-api"
    }

@app.get("/ready")
async def readiness() -> dict[str, Any]:
    """Configuration readiness check; /health remains a cheap liveness probe."""
    required = ("SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY")
    missing = [name for name in required if not os.environ.get(name, "").strip()]
    if missing:
        raise HTTPException(
            503,
            {
                "status": "not_ready",
                "missing_configuration": missing,
            },
        )
    return {
        "status": "ready",
        "checks": {"supabase_configuration": "present"},
    }


async def _fetch_notification_grade_feeds(librus: Librus) -> tuple[dict[str, list[Any]], list[str]]:
    """Fetch optional grade feeds independently so one broken feed cannot hide the others."""
    names = (
        "descriptive_grades",
        "partial_grades",
        "point_grades",
        "text_grades",
        "behaviour_grades",
    )
    results = await asyncio.gather(
        *(getattr(librus, name)() for name in names),
        return_exceptions=True,
    )
    feeds: dict[str, list[Any]] = {}
    failed: list[str] = []
    for name, result in zip(names, results, strict=True):
        if isinstance(result, Exception):
            feeds[name] = []
            failed.append(name)
        else:
            feeds[name] = list(result or [])
    return feeds, failed


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
            try:
                school = await librus.school()
            except Exception:
                # School metadata is helpful for dashboard branding, but a
                # temporary failure must not prevent a valid Librus login.
                school = None
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "student": asdict(me),
            "school": asdict(school) if school is not None else None,
            "session": state,
        })

    except Exception as error:
        raise _safe_error(error) from None


class MessageRequest(Credentials):
    message_id: str = Field(min_length=1, max_length=128)
    mailbox: str = Field(default="inbox", pattern="^(inbox|outbox|archive/inbox|archive/outbox)$")


class MessageAttachmentRequest(Credentials):
    message_id: str = Field(min_length=1, max_length=128)
    attachment_id: str = Field(min_length=1, max_length=128)


class HomeworkAttachmentRequest(Credentials):
    attachment_id: str = Field(min_length=1, max_length=128)


class JustificationSubmitRequest(Credentials):
    date_from: str = Field(min_length=10, max_length=10, pattern=r"^\d{4}-\d{2}-\d{2}$")
    date_to: str = Field(min_length=10, max_length=10, pattern=r"^\d{4}-\d{2}-\d{2}$")
    lessons: list[int] = Field(min_length=1, max_length=20)
    message_from_parent: str = Field(min_length=1, max_length=2000)
    send_notify: bool = True


class MessageSendRequest(Credentials):
    recipient_ids: list[str] = Field(min_length=1, max_length=10)
    topic: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=10000)


class MessageReplyRequest(Credentials):
    message_id: str = Field(min_length=1, max_length=128)
    mailbox: str = Field(default="inbox", pattern="^(inbox|archive/inbox)$")
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
            available_ids = {item.account_id for item in (await librus.message_recipients())}
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


@app.post("/v1/justification/submit")
async def submit_justification(
    payload: JustificationSubmitRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Składanie e-Usprawiedliwień jest dostępne wyłącznie z konta rodzica.")

    try:
        date_from = date.fromisoformat(payload.date_from)
        date_to = date.fromisoformat(payload.date_to)
    except ValueError:
        raise HTTPException(422, "Nieprawidłowy zakres dat.") from None
    if date_to < date_from or (date_to - date_from).days > 31:
        raise HTTPException(422, "Zakres e-Usprawiedliwienia musi mieścić się w 31 dniach.")
    if any(lesson < 1 or lesson > 20 for lesson in payload.lessons):
        raise HTTPException(422, "Numer lekcji musi mieścić się w zakresie 1–20.")
    if not payload.message_from_parent.strip():
        raise HTTPException(422, "Wpisz powód nieobecności.")

    user = await require_user(authorization)
    check_rate_limit(request)

    async with Librus(
        payload.login,
        payload.password.get_secret_value(),
        session_data=_session_data(payload.session),
        cache_reference_data=True,
    ) as librus:
        # Reverse-engineered Librus gateway write endpoint; librus-synergia 0.3.30
        # exposes justification reads but not a high-level submission method.
        # Ensure the authenticated cookie session is ready before the write.
        try:
            await librus.me()
        except Exception as error:
            raise _safe_error(error) from None
        client = librus.client
        endpoint = "https://synergia.librus.pl/gateway/api/2.0/Justifications"
        body = {
            "dateFrom": payload.date_from,
            "dateTo": payload.date_to,
            "messageFromParent": payload.message_from_parent.strip(),
            "lessons": payload.lessons,
            "sendNotify": payload.send_notify,
        }
        headers = {
            "User-Agent": USER_AGENT,
            "Referer": "https://synergia.librus.pl/gateway/ms/studentdatapanel/ui/",
            "Origin": "https://synergia.librus.pl",
            "Accept": "application/json, text/plain, */*",
            "Content-Type": "application/json",
        }

        async def post_once() -> tuple[int, str]:
            async with client._session.post(
                endpoint,
                json=body,
                headers=headers,
                **client._timeout,
            ) as response:
                return response.status, await response.text()

        try:
            status, raw = await post_once()
        except Exception:
            # A timeout after POST is ambiguous: never retry a possibly-sent excuse.
            raise HTTPException(
                502,
                {
                    "message": "Librus nie potwierdził wyniku wysyłki. Sprawdź moduł e-Usprawiedliwienia przed ponowną próbą.",
                    "outcome_unknown": True,
                },
            ) from None

        needs_reauth = status == 401 or (
            status < 400
            and not raw.lstrip().startswith("{")
            and ("<html" in raw.lower() or "loguj" in raw.lower())
        )
        if needs_reauth:
            # A definite 401/login page means the first request was rejected before submission.
            try:
                await librus.login(force=True)
            except Exception:
                raise HTTPException(401, "Sesja Librusa wygasła. Zaloguj się ponownie.") from None
            try:
                status, raw = await post_once()
            except Exception:
                raise HTTPException(
                    502,
                    {
                        "message": "Nie udało się potwierdzić wyniku po odświeżeniu sesji. Sprawdź e-Usprawiedliwienia.",
                        "outcome_unknown": True,
                    },
                ) from None
            if status == 401 or "<html" in raw.lower() or "loguj" in raw.lower():
                raise HTTPException(401, "Librus nadal odrzuca sesję. Zaloguj się ponownie.")

        try:
            parsed = json.loads(raw) if raw.strip() else {}
            result_data = parsed if isinstance(parsed, dict) else {}
        except ValueError:
            result_data = {}

        status_field = str(result_data.get("Status") or result_data.get("status") or "").lower()
        success = status < 400 and status_field != "error" and result_data.get("successful") is not False
        if status >= 500:
            raise HTTPException(
                502,
                {
                    "message": f"Librus zwrócił HTTP {status}. Sprawdź moduł e-Usprawiedliwienia przed ponowną próbą.",
                    "outcome_unknown": True,
                },
            )
        if not success:
            detail = (
                result_data.get("Message")
                or result_data.get("message")
                or (raw[:500] if raw else f"Librus zwrócił HTTP {status}.")
            )
            module_unavailable = status in (403, 404) or any(
                marker in str(detail).lower()
                for marker in ("brak dostępu", "brak dostepu", "niedostęp", "niedostep", "module disabled")
            )
            raise HTTPException(
                status if status in (401, 403, 404) else 400,
                {"message": str(detail), "module_available": not module_unavailable},
            )

        return jsonable_encoder({
            "ok": True,
            "submitted": True,
            "result": result_data,
            "user_id": user["id"],
            "profile": payload.profile,
            "session": asdict(librus.session_data),
        })


@app.post("/v1/homework/attachment")
async def download_homework_attachment(
    payload: HomeworkAttachmentRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Pobieranie załączników zadań jest dostępne wyłącznie z konta rodzica.")

    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            attachment = await librus.download_homework_attachment(payload.attachment_id)
            state = asdict(librus.session_data)

        if len(attachment.content) > 20 * 1024 * 1024:
            raise HTTPException(413, "Załącznik przekracza limit 20 MB.")

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "attachment": {
                "filename": attachment.filename,
                "content_type": attachment.content_type,
                "content_base64": base64.b64encode(attachment.content).decode("ascii"),
            },
            "session": state,
        })

    except HTTPException:
        raise
    except Exception as error:
        raise _safe_error(error) from None


@app.post("/v1/message/attachment")
async def download_message_attachment(
    payload: MessageAttachmentRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Pobieranie załączników jest dostępne wyłącznie z konta rodzica.")

    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            attachment = await librus.download_attachment(
                payload.attachment_id,
                payload.message_id,
            )
            state = asdict(librus.session_data)

        if attachment is None:
            raise HTTPException(404, "Librus nie udostępnił tego załącznika.")
        if len(attachment.content) > 20 * 1024 * 1024:
            raise HTTPException(413, "Załącznik przekracza limit 20 MB.")

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "attachment": {
                "filename": attachment.filename,
                "content_type": attachment.content_type,
                "content_base64": base64.b64encode(attachment.content).decode("ascii"),
            },
            "session": state,
        })

    except HTTPException:
        raise
    except Exception as error:
        raise _safe_error(error) from None


@app.post("/v1/message/reply")
async def reply_to_message(
    payload: MessageReplyRequest,
    request: Request,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if payload.profile != "parent":
        raise HTTPException(403, "Odpowiadanie na wiadomości jest dostępne wyłącznie z konta rodzica.")

    user = await require_user(authorization)
    check_rate_limit(request)

    try:
        async with Librus(
            payload.login,
            payload.password.get_secret_value(),
            session_data=_session_data(payload.session),
            cache_reference_data=True,
        ) as librus:
            sent = await librus.reply_to_message(
                payload.message_id,
                payload.text,
                mailbox=payload.mailbox,
            )
            state = asdict(librus.session_data)

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "sent": asdict(sent),
            "session": state,
        })

    except LibrusMessageSendError as error:
        if error.sent is None:
            raise HTTPException(
                502,
                {
                    "message": "Librus nie potwierdził, czy odpowiedź została wysłana. Sprawdź folder Wysłane przed ponowną próbą.",
                    "sent": None,
                },
            ) from None
        raise HTTPException(
            400,
            {
                "message": "Librus nie wysłał odpowiedzi. Sprawdź, czy wiadomość przyjmuje odpowiedzi.",
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
    payload: SyncRequest,
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
            if payload.notifications_only:
                # Poll the smaller change-tracking snapshot for background
                # notifications. fetch_changes() is about 10 requests vs
                # fetch_all()'s ~35; it omits the newer grade feeds, so fetch
                # those separately and merge them into the same response.
                data = await librus.fetch_changes()
                feeds, failed_grade_feeds = await _fetch_notification_grade_feeds(librus)
                # Keep each feed separate in the notification payload so a
                # failed descriptive feed cannot hide a successful partial feed.
                data.descriptive_grades = feeds["descriptive_grades"]
                data.point_grades = feeds["point_grades"]
                data.text_grades = feeds["text_grades"]
                data.behaviour_grades = feeds["behaviour_grades"]
            else:
                data = await librus.fetch_all()
                failed_grade_feeds = []
            state = asdict(librus.session_data)

        serialized_data = asdict(data)
        if payload.notifications_only:
            # The Android worker must distinguish an unavailable feed from an
            # empty feed; otherwise a transient Librus error could create
            # false "new grade" alerts on the next successful poll.
            serialized_data["partial_grades"] = feeds["partial_grades"]
            serialized_data["failed_sections"] = failed_grade_feeds

        return jsonable_encoder({
            "ok": True,
            "user_id": user["id"],
            "profile": payload.profile,
            "data": serialized_data,
            "session": state,
        })

    except Exception as error:
        raise _safe_error(error) from None
