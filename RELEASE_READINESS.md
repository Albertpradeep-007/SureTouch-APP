# SURE ProEd v1.1.14 Beta release validation

Version code 21; version name `1.1.14-beta`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

- Live-class reminders are emitted at T-5 minutes and class-start events at T with high-priority FCM delivery.
- Connected apps receive the same lifecycle updates immediately through the existing realtime notification path.
- Android renders class pushes directly, preserves separate starting-soon and started events, and prevents an API refresh from duplicating or replacing them.
- Late notifications remain useful: scheduled, server-sent, device-received, and delay timestamps are retained for diagnostics.
- Authenticated delivery acknowledgements provide per-device latency data for the administration dashboard.
- Assignment and live-class screens include the current beta workflow and presentation improvements.

## Verification

- 640 backend regression tests passed; 1 expected skip.
- Django migration drift check passed (`makemigrations --check --dry-run`).
- Android unit tests, release lint, and release APK assembly passed from a clean build.
- 37 connected Android tests passed on the Pixel 8 Android 14 emulator; 1 opt-in live-account test was skipped.
- In-place APK installation and launch passed. The installed package reports version code 21 and version name `1.1.14-beta`.
- APK Signature Scheme v2 verification passed. The signing certificate SHA-256 matches the published v1.1.12-beta APK.
- The APK is not debuggable.

## Artifact

- File: `SURE_ProEd_v1.1.14-beta.apk`
- Size: 16,969,157 bytes (16.18 MiB)
- SHA-256: `b8a0e38c46c8c4126c84e7fb5e1566bfae5d133ad1a9114c58604b4f69709eef`
