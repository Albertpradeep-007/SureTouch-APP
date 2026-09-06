# SURE ProEd v1.1.8 release validation

Published release: Android v1.1.8, version code 15, direct APK update; OTA is active. APK source: `1dd07cedf5a015c30a18fe1dbecfb6111f08f865` on `main`. Backend source: `170249bb07491fb5238731ec5e5846cd9f2fe352` on `Pradeep-Backend-v1`.

See [the onboarding guide](ONBOARDING.md) for the seeded-student, new-student and staff workflows.

## Root cause and final behavior

A student primary email was also a volunteer notification/mapped email. The former unordered email query could return the volunteer account, while the APK inferred a Student role from an email search. That produced a student dashboard for the wrong authenticated account. The actual seeded student profile, G2-26 application and evaluated grades remained intact.

The backend now considers primary and mapped addresses and requires an explicit existing role only when an email matches multiple roles. The chooser returns role labels only, with no profile IDs, internal email addresses or academic records. Single-account emails use normal login. Choosing a role never creates an account, changes a role, or bypasses that account's password. Multiple same-role aliases remain ambiguous and require a unique primary address.

Password-reset request and confirmation carry the selected role. OTPs are stored against the canonical account, so a code for one account cannot reset another account sharing its inbox. Existing in-flight reset challenges are retired during rollout. The APK resolves every completed authentication from `/api/users/me/`, clears legacy sessions once, and clears account caches and notifications when accounts change.

Student accounts cannot have mapped emails. Django admin add/change forms and the API report a field error. Model saves raise a validation error instead of silently deleting the input. Database constraint `student_has_no_mapped_email` also blocks bulk updates. The production audit found zero existing students with prohibited mappings, so no account data cleanup was needed. Staff mapped-email access remains supported.

Volunteers default to assigned cohorts. Only administrators can grant wider access. Attendance queries and caches respect enrollment and assignment boundaries. Explicit historical presence and absence snapshots are retained. Your colleague's Meet matching updates were preserved; cleanup clears expired meeting access links while retaining academic attendance snapshots.

## Expected student states

| Situation | Expected result |
|---|---|
| Single-account email | No role chooser |
| Email shared by Student and Volunteer | Only Student and Volunteer choices |
| Reset Student password | Volunteer password and records unchanged |
| New student without cohort | Pending enrollment, no unrelated attendance or invented grades |
| Seeded enrolled student | Own cohort/application, stored marks and eligible attendance |
| Missing or unpublished results | Pending/empty state rather than invented absence or pass |
| Evaluated zero marks | Real zero result displayed |
| Network failure or account change | Retry/error; no other account's cached records |

## Validation evidence

- Earlier combined backend regression suite: 162 tests passed.
- Final account and mapped-email suite: 61 tests passed locally.
- Final VM account, mapped-email and Meet regression suite: 64 tests passed in an isolated in-memory database with network disabled.
- Final Android build: 31 unit tests and 17 Android 14 emulator tests passed; release assembly and release lint passed.
- The APK signer matches the published predecessor; installation over version code 13 passed. The public GitHub APK was downloaded and its SHA-256 matched `9dc672b47da588b9aae374052bbfa720a3b98cf55a7b4f82c0cd34256735b488` (15,584,539 bytes). The public OTA endpoint serves code 15. The VM business-data fingerprints were unchanged by deployment.
- A follow-up onboarding audit found and fixed login reactivation of disabled accounts. Password reset also preserves administrator activation/verification flags and rejects codes for accounts disabled after issuance. The expanded local onboarding/access suite passed 116 tests.
- All 34 production student accounts are active, verified and have profiles. Two enrolled profiles are missing college and degree; these require verified details from the students/admin, not placeholders. There are 30 TRAINING applications and 3 APPLIED applications; pending applications are expected to follow the admission journey.
- Production rollback backup: `/home/dev1/release-backups/20260906T071414Z-identity`. Includes database dump, source archive, prior Git bundle and colleague's original dirty changes. The colleague's edits are also retained in a named VM Git stash.

## Rollout and remaining limits

After installing this update, sign in again. For the affected seeded student, request a NEW password-reset OTP and choose Student when prompted. The previous reset may have changed the separate volunteer account. Do not merge/delete these accounts or copy password hashes between them.

The direct APK retains the existing published signing certificate for installation compatibility. That certificate is currently an Android Debug certificate; changing it casually would break existing direct upgrades. The APK itself is not debuggable. A protected production signing-key migration remains separate work.

The historical WhatsApp attendance workbook contains 50 sessions for 27 mapped students. Those historical rows have not been imported. The existing importer changes staff/mentor assignments and generates synthetic Google Meet timestamps, so it was not run. This release preserves existing database attendance; it does not claim the entire workbook history has been backfilled.

Real mailbox OTP delivery and installation on every physical phone are not covered by emulator or mocked-email tests. SMTP connection/authentication was checked without sending unsolicited mail. API schema generation still has pre-existing documentation warnings. The tested isolation fixes are not a guarantee that every unrelated production endpoint has received a complete security audit.
