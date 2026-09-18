# SURE ProEd v1.1.15-beta.2 release validation

Version code 25; version name `1.1.15-beta.2`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

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
- SHA-256: `b784f7dd9bcc63d3dd47a395222ca88e1df75b634a93c8cd1227ec72f2224bf9`
