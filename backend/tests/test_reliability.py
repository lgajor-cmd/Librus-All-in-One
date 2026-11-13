import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException
from starlette.requests import Request

from app.main import (
    _api_error,
    _fetch_notification_grade_feeds,
    readiness,
    check_rate_limit,
    _hits,
)


class NotificationGradeFeedTests(unittest.IsolatedAsyncioTestCase):
    async def test_all_grade_feeds_are_returned(self):
        librus = SimpleNamespace(
            descriptive_grades=AsyncMock(return_value=[{"id": "d1"}]),
            partial_grades=AsyncMock(return_value=[{"id": "p1"}]),
            point_grades=AsyncMock(return_value=[{"id": "pt1"}]),
            text_grades=AsyncMock(return_value=[{"id": "t1"}]),
            behaviour_grades=AsyncMock(return_value=[{"id": "b1"}]),
        )

        feeds, failed = await _fetch_notification_grade_feeds(librus)

        self.assertEqual(feeds["descriptive_grades"], [{"id": "d1"}])
        self.assertEqual(feeds["partial_grades"], [{"id": "p1"}])
        self.assertEqual(feeds["point_grades"], [{"id": "pt1"}])
        self.assertEqual(feeds["text_grades"], [{"id": "t1"}])
        self.assertEqual(feeds["behaviour_grades"], [{"id": "b1"}])
        self.assertEqual(failed, [])

    async def test_one_failed_feed_does_not_discard_other_feeds(self):
        librus = SimpleNamespace(
            descriptive_grades=AsyncMock(return_value=[{"id": "d1"}]),
            partial_grades=AsyncMock(side_effect=RuntimeError("temporary Librus error")),
            point_grades=AsyncMock(return_value=[{"id": "pt1"}]),
            text_grades=AsyncMock(return_value=[]),
            behaviour_grades=AsyncMock(return_value=[{"id": "b1"}]),
        )

        feeds, failed = await _fetch_notification_grade_feeds(librus)

        self.assertEqual(feeds["descriptive_grades"], [{"id": "d1"}])
        self.assertEqual(feeds["partial_grades"], [])
        self.assertEqual(feeds["point_grades"], [{"id": "pt1"}])
        self.assertEqual(feeds["behaviour_grades"], [{"id": "b1"}])
        self.assertEqual(failed, ["partial_grades"])


class ReadinessTests(unittest.IsolatedAsyncioTestCase):
    async def test_ready_when_supabase_configuration_is_present(self):
        with patch.dict(os.environ, {
            "SUPABASE_URL": "https://example.supabase.co",
            "SUPABASE_PUBLISHABLE_KEY": "test-publishable-key",
        }):
            result = await readiness()

        self.assertEqual(result["status"], "ready")
        self.assertEqual(result["checks"]["supabase_configuration"], "present")

    async def test_not_ready_when_supabase_configuration_is_missing(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(HTTPException) as raised:
                await readiness()

        self.assertEqual(raised.exception.status_code, 503)
        self.assertIn("SUPABASE_URL", raised.exception.detail["missing_configuration"])
        self.assertIn("SUPABASE_PUBLISHABLE_KEY", raised.exception.detail["missing_configuration"])



class RateLimitTests(unittest.TestCase):
    def setUp(self):
        _hits.clear()

    def tearDown(self):
        _hits.clear()

    def test_rejects_request_after_ten_per_minute(self):
        request = Request({
            "type": "http",
            "method": "GET",
            "path": "/v1/sync",
            "headers": [],
            "query_string": b"",
            "scheme": "https",
            "client": ("203.0.113.77", 54321),
            "server": ("testserver", 443),
        })

        for _ in range(10):
            check_rate_limit(request)

        with self.assertRaises(HTTPException) as raised:
            check_rate_limit(request)

        self.assertEqual(raised.exception.status_code, 429)

class ApiErrorTests(unittest.TestCase):
    def test_machine_readable_error_code_preserves_text_detail(self):
        error = _api_error(401, "SUPABASE_SESSION_EXPIRED", "Sesja wygasła.")

        self.assertEqual(error.status_code, 401)
        self.assertEqual(error.detail, "Sesja wygasła.")
        self.assertEqual(error.headers["X-Librus-Error-Code"], "SUPABASE_SESSION_EXPIRED")


if __name__ == "__main__":
    unittest.main()
