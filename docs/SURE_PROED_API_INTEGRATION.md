# SURE ProEd Android API integration

This app's Retrofit contract was synchronized with `Sure ProEd Platform API.yaml` on 04 Aug 2026.

## Runtime configuration

- Retrofit base URL: `ApiClient.BASE_URL`
- Authentication: JWT access token in `Authorization: Bearer <token>`
- Refresh: `POST auth/token/refresh/`
- All resource paths below are relative to the `/api/` base URL.
- Django REST Framework list responses use `PaginatedResponse<T>`.

## Wired endpoint families

| Resource | Endpoints wired |
|---|---|
| Authentication | `POST auth/token/`, `POST auth/token/refresh/` |
| Users | list, create/register, retrieve, replace, patch, delete |
| Students | list, create, retrieve, replace, patch, delete |
| Courses | list, create, retrieve, replace, patch, delete |
| Applications | list, create/apply, retrieve, replace, patch, delete, assign cohort, check completion |
| Cohorts | list, create, retrieve, replace, patch, delete |
| Attendance | list, create, retrieve, replace, patch, delete |
| Assignments | list, create, retrieve, replace, patch, delete |
| Submissions | list, create/submit, retrieve, replace, patch, delete |
| Questions | list, create, retrieve, replace, patch, delete |
| Exams | list, create, retrieve, replace, patch, delete, submit |
| Certificates | list, create, retrieve, replace, patch, delete, verify |
| Companies | list, create, retrieve, replace, patch, delete |
| LinkedIn | connect URL, callback, disconnect |
| Student journey | `GET dashboard/student-journey/` for backend-owned academic, training, social-activity, verification, and certification progress |
| Notifications | `GET notifications/`, Android unread delivery, permission/settings UI, and native academic/learning/community/achievement channels |

Student dashboard extensions are documented in `REALTIME_AND_SCALABILITY.md`, including the aggregated dashboard read, verified-student/cohort lifecycle, unread notification and certificate counts, and module-test progression contract.

The deployed portal also exposes `GET/PATCH profiles/me/`, `GET notifications/`, and the preferred `GET dashboard/student-journey/` progress read; these are retained as deployment extensions even though they are not present in the supplied OpenAPI paths. Certificate, notification, mentor, and journey screens render backend state rather than design-preview records.

## Model coverage

`AppModels.kt` includes the documented fields for applications, assignments, attendance, certificates, cohorts, companies, courses, exams, questions, student profiles, and submissions. JSON snake-case fields are mapped with `@SerializedName`.

Profile UI fields such as parent names, addresses, date of birth, gender, and cohort code are nullable deployment extensions. The screen displays `Not provided` until the backend returns them; it does not invent personal data.

## Production connection checklist

1. Set `ApiClient.BASE_URL` to the production HTTPS API URL.
2. Remove cleartext HTTP allowance from `AndroidManifest.xml` when HTTPS is available.
3. Confirm the production serializer returns a nested user object for `StudentProfileDto.user`; the OpenAPI schema currently describes that field as a UUID.
4. Confirm the certificate verification query name (`verification_code`) with the production server.
5. Populate attendance records with `class_date`, start/end times, title, trainer, notes, and cohort; both dashboard and timetable screens now render this API data without design-preview sessions.
6. `PATCH profiles/me/` accepts `profile_photo` as a Base64 data URL. Reads may return that Base64 value or an absolute HTTPS URL; the Android client renders both formats.

Admin-only create/update/delete calls are exposed in the client contract but should only be used from authorized roles. Student screens currently consume the appropriate list, detail, apply, submit, and profile calls.
