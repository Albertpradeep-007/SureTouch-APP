# SURE ProEd v1.1.15-beta.2 release validation

Version code 25; version name `1.1.15-beta.2`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

- Fixed "Forgot Password" unauthenticated request failure:
  - In `ApiClient.kt`, added `isPublicPath()` and ensured public authentication/recovery endpoints (`users/forgot_password_request/`, `users/forgot_password_confirm/`, `auth/token/`, OTP verification, and version checks) do NOT attach stale or expired `Authorization: Bearer` tokens.
  - Excluded public endpoints from OkHttp `authenticator` to prevent token refresh loops on 401s.
  - In Django backend `accounts/views.py`, added `authentication_classes=[]` to `@action(permission_classes=[AllowAny])` on `forgot_password_request` and `forgot_password_confirm` to prevent DRF JWT authentication failure when an unauthenticated caller sends a stale header.
  - Updated OTP input `keyboardOptions` to `KeyboardType.Number` for standard numeric keyboard behavior across Android devices.
- Fixed Forgot Password modal bottom sheet in Light Mode:
  - Applied `SureFormDefaults.outlinedTextFieldColors()` ensuring dark high-contrast readable text (`onSurface`) and background (`surface`) in light mode without washed-out/invisible inputs.
  - Added leading icons (`Icons.Default.Email`, `Icons.Default.Pin`, `Icons.Default.Lock`), rounded input corners (`14.dp`), and themed buttons.
  - Added step indicator badge ("Step 1 of 2: Registered Email") and header lock reset badge.
  - Themed error banner with clear contrast and error icon.
- Fixed `SureTrustLogo` surface background to use `MaterialTheme.colorScheme.surface` instead of hardcoded white, preventing harsh white background artifacts on dark surfaces.
- Role-based permissions to strictly prevent students from unsuspending applications.
- Backend permission synchronization (`permissions`, `user_permissions`, `prevent_unsuspend`).
- Multi-select batch student suspension and unsuspension in both Mentor and Trustee/Volunteer dashboards.
- Attendance roster single and batch unsuspend actions gated by permission policy.
- Fixed button row wrapping in session cards to prevent text wrapping ("Delete" no longer wraps into two lines).
- Context-aware "End Class" action visibility based on active/ongoing session status.
- UI improvements across volunteer dashboard, announcements, and timetables.

## Verification

- Android unit tests passed (`testDebugUnitTest`).
- Release APK compilation and packaging passed (`assembleRelease`).
- Verified on Android emulator: installed and validated clean UI launch without session loss.

## Artifact

- File: `SURE_ProEd_v1.1.15-beta.2_release.apk`
- Size: 17,339,993 bytes
- SHA-256: `a4b04b3c4d09fd84640e4a95f9af754b5a6e79252a992ba28b23199650c7d3ae`
