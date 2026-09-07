# SURE ProEd v1.1.10 release validation

Version code 17; direct APK update. Backend branch: Pradeep-Backend-v1. Android branch: main.

## Verified

- 69 backend regression tests passed after merging the VM attendance changes.
- 18 account tests passed after preserving the VM volunteer category changes.
- Android unit tests, release lint/build, and 19 emulator tests passed.
- Upgrade from v1.1.9 passed; signing certificate matches the published app; release APK is not debuggable.
- Backend deployed from c71acd57774cf5c1aeaf3df2fff77722b5734bce. Database backup validated; main record counts preserved. Gunicorn, Celery, Celery Beat and Nginx are active.
- Firebase credential authentication and both .env configurations verified. Private JSON files are outside the repositories; project ID and credential path are configured in .env.

## Attendance and identity

Seeded students retain their existing account, cohort, history and grades when resetting the selected Student account password. The role chooser appears only for multiple matching accounts. Students cannot have mapped emails; staff mappings remain supported.

Verified Google email identity takes precedence over display names. Initials, word order, known cohort/course suffixes and spelling similarity produce roster-scoped review suggestions. Similarity scores are not identity proof or probabilities. Uncertain identities are excluded from finalized totals and automatic attendance penalties. Missed module tests preserve cohort access for administrator review.

Student Join actions open from ten minutes before start and are checked by the backend. A portal click records supporting evidence; it does not award attendance.

## Notifications and documents

FCM transports notification IDs and login-session identifiers. The app retrieves notification text through its authenticated API and ignores messages for previous login sessions. Android notification permission is required; force-stop and device restrictions can interrupt delivery.

Profile banners synchronize through authenticated uploads. Private resumes require authenticated access; anonymous offer-letter media routes are blocked.

## Artifact

SHA-256: f4f7781d4ed6e3407d501ab9856145a469907ee0fe14ba49a1d7e9af9e1ddad5
Size: 16854349 bytes.

Live seeded-student API verification: Student identity, G2-26 cohort, TRAINING status, 50/51 attendance (98.04%); all queried academic endpoints returned HTTP 200. Anonymous private-document probes returned 404.

The disposable-account registration test passed against the live FCM endpoint.

End-to-end background push verified: a notification sent by the VM arrived in Android notifications while the emulator was on Home. Only a disposable test account was used.
