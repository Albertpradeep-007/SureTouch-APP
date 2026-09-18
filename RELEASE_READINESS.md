# SURE ProEd v1.1.16 release validation

Version code 26; version name `1.1.16`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

- **Account Creation (Sign-Up Flow) Stability**:
  - Added real-time client-side password strength validation (min 8 characters, uppercase, lowercase, number, special symbol) in `AuthScreen.kt`.
  - Added inline helper text for password rules to prevent unexpected backend validation rejections.
  - Ensured registration OTP dispatch and verification endpoints are immune to stale/invalid `Authorization: Bearer` headers.
  - Seamless auto-login with fresh JWT tokens upon email verification.
- **Forced Session Revocation (Auto-Logout on Password Change)**:
  - Cryptographic token fingerprinting via `pwd_hash` embedded in JWT claims.
  - Instant invalidation across all active sessions and devices when password is changed or reset.
  - Android client `ApiClient.kt` catches 401 `password_changed` and triggers fast-path session logout without looping.
  - Foreground periodic pulse (every 12s) and `ON_RESUME` check in `MainActivity.kt` ensuring idle secondary devices (Phone B) are immediately redirected to login screen with backstack cleared.
- **In-App Password Management**:
  - Added `ChangePasswordDialog.kt` accessible from the Profile screen with live strength checks and automatic credential refresh.
- **Clean Re-Authentication**:
  - Clean login using updated credentials without stale token conflicts or cache corruption.
- **Forgot Password Endpoint & Light Mode UI**:
  - High-contrast text and background fields, rounded corners, themed buttons, and numeric OTP keyboard.

## Verification

- Android unit tests passed (`testDebugUnitTest`).
- Android release APK compilation and packaging passed (`assembleRelease`).
- Backend automated unit tests passed: 85 of 85 tests passed, including `test_session_revocation.py`.
- Deployed and live-tested on production VM (`https://api.sureproed.com`).

## Artifact

- File: `SURE_ProEd_v1.1.16_release.apk`
- Size: 17,356,365 bytes
- SHA-256: `4229d0e6a527d2605c3486da203cd4106a4c217900f03ea096361fc1a002b253`
