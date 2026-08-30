# Dashboard, module grades, and scalability contract

## Account-to-dashboard flow

1. `POST users/` creates the student.
2. The app immediately authenticates with `POST auth/token/` when the server is available.
3. The authentication route is removed from the navigation back stack and the dashboard is shown.
4. An offline-created local account also reaches the dashboard, but no fake bearer token is sent to the API.
5. The dashboard refreshes when it enters composition and whenever the cohort chip's refresh action is pressed.
6. The latest successfully fetched cohort code is cached locally so transient network failures do not erase an assignment.

## Preferred dashboard read model

Production should implement:

`GET /api/dashboard/student-summary/`

The expected payload is `StudentDashboardSummaryDto` and includes the current application, screening exam, student-role verification flag, assigned cohort, mentor, course, upcoming sessions, module tests/results, certificate count, and unread-notification count. One authorization-aware query endpoint avoids a multi-request fan-out for every active student. The Android repository automatically falls back to existing REST resources while the endpoint is being deployed.

The lifecycle fields used by the unchanged dashboard layout are:

- `application.status` and `screening_exam.status` for application and exam progress;
- `student_role_verified` after a qualified pre-screening result;
- `cohort` only after assignment;
- `certificate_count` and `unread_notification_count` for truthful shortcut/badge states.

The pre-screening page derives admission stages from the existing profile, application, exam, and cohort records. After cohort assignment it reads `GET /api/dashboard/student-journey/` and shows this fixed pathway: classes and module tests, assignments, capstone/projects, Life Skills and Soft Skills training, tree plantation, blood donation and helping-society activity, requirements verification, and the official SURE ProEd certificate. Every extension flag defaults to false, so no later stage is shown as completed until backend progress advances it.

The journey payload supports these backend-owned flags:

- `attended_classes`, `module_tests_completed`, `assignments_submitted`;
- `projects_completed` or the backward-compatible `capstone_completed`;
- `life_skills_training_completed`, `soft_skills_training_completed`;
- `tree_plantation_completed`, `blood_donation_completed`, `helping_society_activity_completed`;
- `requirements_verified` or the backward-compatible `requirements_completed`;
- `certificate_issued`, plus optional `stage_dates` keyed by those field names.

## Native Android notifications

Android 13 and newer request `POST_NOTIFICATIONS` from the in-app Account Notifications screen. The app creates separate academic, learning, community, and achievement channels, posts only unread backend messages not already delivered on the device, uses Android's required monochrome status icon, and displays the official SURE TRUST logo as the notification artwork. Tapping a notification opens Account Notifications from both a cold start and an already-running app. The same screen provides Enable and Settings controls; test-notification UI is not included in the student release.

For reliable delivery while the app is fully closed, production should publish the same notification payload through Firebase Cloud Messaging and use the server notification ID as the idempotency key. The current client also synchronizes unread `GET /api/notifications/` records whenever an authenticated app session starts or the notification screen refreshes.

Recommended server behavior:

- Redis-cache the summary by `student_id` for 15–30 seconds.
- Invalidate that key when a cohort is assigned, attendance is scheduled, or a test result is committed.
- Use `select_related`/`prefetch_related` for cohort, course, sessions, tests, and results.
- Return ETag/`Last-Modified` headers so unchanged payloads can use `304 Not Modified`.
- Paginate every unbounded administrative list; the summary itself must remain bounded.

## Module test progression

The Android client contract adds:

- `GET /api/module-tests/`
- `GET /api/module-test-results/`
- `POST /api/module-tests/{id}/submit/`

The server is the authority for unlocking. Submitting a test should use one database transaction:

1. Lock the student's current module-progress row.
2. Reject submission if the module is locked or the student is not in the course cohort.
3. Grade once using an idempotency key to prevent duplicate results.
4. Store marks, maximum marks, percentage, pass/fail, attempt number, and completion time.
5. Unlock exactly the next module only when the configured pass percentage is met.
6. Invalidate the dashboard-summary cache after commit.

The app independently renders later modules as locked until the preceding result is passed, but client-side locking is presentation only and must not replace server authorization.

## Android performance controls now present

- Thread-safe singleton Retrofit service.
- Reused OkHttp connection pool.
- Bounded global and per-host request concurrency.
- Parallel fallback dashboard reads instead of serial latency; certificate and notification state are included in the same coalesced refresh.
- Mutex-based request coalescing and a 15-second dashboard cache.
- Bounded connect/read/write/call timeouts with connection retry.
- BASIC logging instead of response BODY logging.
- No polling loop; refresh occurs on dashboard entry or explicit cohort refresh.

## “Total booking” investigation

There is no booking model, endpoint, screen, string, or “total booking” section in this Android repository or the supplied OpenAPI document. Consequently there is no in-scope booking query to optimize here.

If “total booking” belongs to another service, inspect its server query plan and apply, as appropriate:

- a composite index matching tenant/user, status, and created-date filters;
- a pre-aggregated counter or materialized summary instead of `COUNT(*)` across joins;
- cursor pagination instead of loading all bookings;
- removal of per-row/N+1 lookups;
- short-lived caching with write-side invalidation;
- latency, database-time, cache-hit, row-count, and payload-size metrics.

The owning backend endpoint and repository are required before a concrete booking fix can be made or verified.
