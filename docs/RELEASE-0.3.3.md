# Librus All-in-One 0.3.3

- Android version code: 6
- Improved reliability of background notification checks and retry handling.
- Added notification diagnostics and an on-device test notification.
- Prevented newly detected items from being marked as delivered when Android blocks notification posting.
- Added SHA-256 fallback identity for records without a stable identifier.
- Added stable release signing support in GitHub Actions.
- CI verifies the release APK certificate fingerprint before publishing the release artifact.

Build and installation notes:
- The release APK is published as `Librus-All-in-One-APK-RELEASE` only when all four Android signing secrets are configured.
- Without signing secrets, CI publishes only a `DEBUG-ONLY` artifact. Do not install it as an update to a release build.
- APKs from older ephemeral GitHub debug builds may not accept an update signed with the stable release key. Do not uninstall an existing installation until its local data has been safely migrated.
- Background notification checks use Android WorkManager. They are periodic, not instant push delivery; Android may defer them under battery optimization.
