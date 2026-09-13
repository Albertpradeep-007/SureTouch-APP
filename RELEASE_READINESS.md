# SURE ProEd v1.1.14 Beta 3 release validation

Version code 23; version name `1.1.14-beta.3`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

- Live-class reminders are emitted at T-5 minutes and class-start events at T with high-priority FCM delivery.
- Connected apps receive the same lifecycle updates immediately through the existing realtime notification path.
- Android renders class pushes directly, preserves separate starting-soon and started events, and prevents an API refresh from duplicating or replacing them.
- Late notifications remain useful: scheduled, server-sent, device-received, and delay timestamps are retained for diagnostics.
- Authenticated delivery acknowledgements provide per-device latency data for the administration dashboard.
- Assignment and live-class screens include the current beta workflow and presentation improvements.
- Relative media URLs, legacy `sureproed.com/api` and `sureproed.com/media` URLs, and retired direct-IP URLs are migrated to `https://api.sureproed.com` inside the APK.
- Production deep links no longer accept cleartext HTTP or the retired direct IP.

## Verification

- 640 backend regression tests passed; 1 expected skip.
- Django migration drift check passed (`makemigrations --check --dry-run`).
- All 67 Android unit tests, release lint, and release APK assembly passed.
- 36 connected Android tests completed with 0 failures on the Pixel 8 Android 14 emulator; the opt-in disposable live-account verification remained skipped by assumption.
- In-place release APK installation and launch passed without clearing the logged-in session. The installed package reports version code 23 and version name `1.1.14-beta.3`.
- APK Signature Scheme v2 verification passed. The signing certificate SHA-256 matches the published v1.1.12-beta APK.
- The APK is not debuggable.

## Artifact

- File: `SURE_ProEd_v1.1.14-beta.3.apk`
- Size: 16,969,033 bytes (16.18 MiB)
- SHA-256: `1ba721eafc99093fb26e5a6a2a7c99d6628e1f359c7b880cce06e04a9609b7e1`
