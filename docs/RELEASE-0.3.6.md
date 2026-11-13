# Release 0.3.6

## Reliability

- Parse Librus dates consistently across ISO date-only values, ISO timestamps with timezone offsets, and legacy Polish date formats.
- Reject malformed or impossible dates instead of silently accepting a valid prefix.
- Add unit tests for UTC timestamps, fractional seconds, offsets, legacy values, malformed suffixes, and leap years.

## Upgrade

This release keeps the existing application ID and stable release signing configuration. Install it over the existing signed release; do not uninstall the app to update.
