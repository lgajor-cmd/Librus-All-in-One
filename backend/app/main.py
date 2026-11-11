
"""Stateless HTTPS adapter for Librus Synergia.

Credentials and session cookies are never stored on the server.
"""
from __future__ import annotations

import os
import secrets
import time
from collections import defaultdict, deque
from dataclasses import asdict
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse
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

app = FastAPI(title="Librus All-in-One API", version="0.2.0")

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


def require_api_key(x_api_key: str | None) -> None:
    expected = os.environ.get("API_TOKEN", "")
    if len(expected) < 32:
        raise HTTPException(
            503,
            "API nie jest jeszcze skonfigurowane. Administrator musi ustawić API_TOKEN.",
        )
    if not x_api_key or not secrets.compare_digest(x_api_key, expected):
        raise HTTPException(401, "Brak lub nieprawidłowy klucz API.")


def check_rate_limit(request: Request) -> None:
    ip = request.client.host if request.client else "unknown"
    now = time.monotonic()
    hits = _hits[ip]
    while hits and now - hits[0] > _WINDOW:
        hits.popleft()
    if len(hits) >= _LIMIT:
        raise HTTPException(429, "Za dużo żądań. Spróbuj ponownie później.")
    hits.append(now)


class SessionCookie(BaseModel):
    model_config = ConfigDict(extra="ignore")
    name: str = Field(min_length=1, max_length=64)
    value: str = Field(min_length=1, max_length=8192)
    domain: str = Field(min_length=1, max_length=128)
    path: str | None = Field(default=None, max_length=256)


class SessionState(BaseModel):
    model_config = ConfigDict(extra="ignore")
    cookies: list[SessionCookie] = Field(default_factory=list, max_length=32)
    logged_in_at: float = 0.0


class Credentials(BaseModel):
    model_config = ConfigDict(extra="forbid")
    profile: str = Field(pattern="^(parent|student)$")
    login: str = Field(min_length=1, max_length=128)
    password: SecretStr = Field(min_length=1, max_length=512)
    session: SessionState | None = None


def _session_data(value: SessionState | None) -> LibrusSessionData | None:
    if value is None:
        return None
    return LibrusSessionData(
        cookies=[cookie.model_dump(exclude_none=True) for cookie in value.cookies],
        logged_in_at=value.logged_in_at,
    )


def _safe_error(error: Exception) -> HTTPException:
    if isinstance(error, LibrusCaptchaRequiredError):
        return HTTPException(
            428,
            "Librus wymaga dodatkowej weryfikacji. Zaloguj się w oficjalnym portalu.",
        )
    if isinstance(error, LibrusInvalidCredentialsError):
        return HTTPException(401, "Librus odrzucił login lub hasło.")
    if isinstance(error, LibrusAccountActionRequiredError):
        return HTTPException(
            409,
            "Librus wymaga działania na koncie. Zaloguj się w oficjalnym portalu.",
        )
    if isinstance(error, LibrusAuthError):
        return HTTPException(401, "Nie udało się uwierzytelnić w Librusie.")
    if isinstance(error, LibrusConnectionError):
        return HTTPException(503, "Librus jest chwilowo niedostępny. Spróbuj ponownie.")
    return HTTPException(502, "Błąd komunikacji z Librusem.")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "librus-all-in-one-api"}


@app.post("/v1/connect")
async def connect(
    payload: Credentials,
    request: Request,
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> dict[str, Any]:
    require_api_key(x_api_key)
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
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> dict[str, Any]:
    require_api_key(x_api_key)
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
            "profile": payload.profile,
            "data": asdict(data),
            "session": state,
        })
    except Exception as error:
        raise _safe_error(error) from None
