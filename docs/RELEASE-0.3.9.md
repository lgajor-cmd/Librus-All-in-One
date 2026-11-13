# Release 0.3.9

## Background notification reliability

- Record which Librus data feeds were unavailable during the first notification baseline.
- Seed a recovered feed's current IDs before treating later additions as new, preventing old historical grades from generating false alerts.
- Preserve previous IDs while a feed remains unavailable.
- Add regression tests for partial first sync, feed recovery, later new grades, and legacy notification-state migration.

## Upgrade

This release keeps the existing application ID and stable release signing configuration. Install it over the existing signed release; do not uninstall the app to update.
