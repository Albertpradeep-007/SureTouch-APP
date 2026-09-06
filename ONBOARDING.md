# Production onboarding — SURE ProEd

Use [APK v1.1.8](https://github.com/Albertpradeep-007/SureTouch-APP/releases/tag/v1.1.8), version code 15. The in-app OTA endpoint also serves this release.

## Existing seeded students

1. Install the update and sign in again. An older saved session is cleared once.
2. Use the student's registered primary email. Do not create another account for a student already seeded.
3. Use **Forgot password** to set a personal password. If the email matches multiple roles, choose **Student**, then request the verification code.
4. Enter the newest OTP and a strong password. Sign in to the Student account with that password.
5. Check the student code, enrolled course/cohort and published marks. The dashboard must show the student's existing enrollment; the student does not need to reapply for that course.

An email belonging to just one account does not show a role chooser. A shared email shows only its matching roles. Selecting Volunteer or Mentor opens that separate existing account and requires its password. A password reset affects only the chosen account.

## New students

Register once using a primary email, verify its OTP, and complete personal/contact and education details. Follow the application journey for the selected course: required professional-profile links, screening, any required interview, review and cohort assignment. Until enrollment, cohort-dependent screens should show pending/empty states rather than another cohort's records. Administrators may use the existing audited direct-enrollment workflow for approved students.

Students must not have mapped emails. Django admin, the API, model saves and database writes reject that combination. Staff retain their mapped email for supported login and notifications.

## Staff and administrators

- Sign in using the staff primary email or mapped email. If prompted, choose the intended matching role.
- Volunteers see assigned cohorts by default. An administrator must explicitly grant broader cohort access.
- Disabling an account prevents login and password reset. A login attempt or an old reset code cannot reactivate it; an administrator must review and re-enable it.
- Missing profile details must be supplied by the student or an administrator using verified information. Do not fill them with placeholders just to remove an incomplete-profile notice.

## Support checks

If a student sees the wrong role or cohort, confirm the installed version, sign out, choose Student on the next sign-in, and compare the student code with Django admin. Do not merge/delete accounts or rerun seed scripts to resolve an identity issue.

If a reset code does not arrive, check spam and use the latest requested code. Staff codes go to the configured notification inbox. Respect the three-per-day reset request limit; support should investigate delivery rather than repeatedly resending.

Published marks and attendance are the records currently stored in the backend. The separate 50-session historical attendance workbook has not been imported. Do not promise its historical percentage is already present in the app.

The release has automated backend and emulator coverage. A representative student should still complete a real mailbox reset and sign in on their own phone during the first onboarding session; those external inbox/device steps cannot be established by mocked-email tests.
