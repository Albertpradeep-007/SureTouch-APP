# SURE ProEd v1.1.14 release validation

Version code 24; version name `1.1.14`. Android branch: `main`. Backend branch: `Pradeep-Backend-v1`.

## Included changes

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

- File: `SURE_ProEd_v1.1.14_release.apk`
- Size: 17,339,977 bytes
- SHA-256: `c0628d8a482bd9f8859ff1f7313998796a7c2b10ea3a92e97fd6ee0d17f182b2`
