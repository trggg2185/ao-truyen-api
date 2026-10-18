# My Manga App

Backend REST API for a manga-reading and translation-group platform. It lets readers
browse manga, read chapters, and discover translation groups, lets users apply to join
or create a translation group, and gives admins review and management tools.

## Features

- Registration, login, JWT introspection, refresh, and logout
- Role- and permission-based authorization (`ADMIN`, `USER`, `TRANSLATOR`)
- Translation groups with leader/member model, join requests, and creation requests
- Manga, category, chapter, and page management
- Manga and translation-group cover image URLs
- Image upload and WebP optimization backed by Cloudflare R2
- Redis for rate limiting, invalidated tokens, and buffered chapter views
- Scheduled background jobs for view flushing and temporary-file cleanup

## Tech stack

- Java 21
- Spring Boot 3.5.14 (Web, Validation, Security, AOP, Actuator)
- Spring Data JPA with MySQL
- Spring Data Redis
- Nimbus JOSE JWT 10.5
- MapStruct 1.6.3
- Lombok 1.18.38
- AWS SDK S3 2.29.15 (Cloudflare R2)
- Slugify 2.4, Thumbnailator, WebP ImageIO
- Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Requirements

- JDK 21
- MySQL
- Redis
- A Cloudflare R2 bucket and credentials

## Configuration

Copy `.env.example` to `.env`, or export the same variables into the process
environment. `application.yaml` reads them through `${...}` placeholders.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | MySQL JDBC URL |
| `DB_PASSWORD` | MySQL password (username is hard-coded as `root`) |
| `SIGNER_KEY` | JWT HS512 signing key |
| `ACCESS_TOKEN_VALIDITY_IN_SECONDS` | Access-token lifetime |
| `REFRESHABLE_DURATION_IN_SECONDS` | Window in which a token may still be refreshed |
| `REDIS_HOST` | Redis host (default `localhost`) |
| `REDIS_PORT` | Redis port (default `6379`) |
| `REDIS_PASSWORD` | Redis password (default empty) |
| `REDIS_DATABASE` | Redis logical DB (default `0`) |
| `CLOUDFLARE_R2_ACCESS_KEY_ID` | R2 access key |
| `CLOUDFLARE_R2_SECRET_ACCESS_KEY` | R2 secret key |
| `CLOUDFLARE_R2_ENDPOINT_URL` | R2 S3-compatible endpoint |
| `CLOUDFLARE_R2_BUCKET_NAME` | R2 bucket name |
| `CLOUDFLARE_R2_PUBLIC_URL` | Public URL prefix for uploaded files (no trailing slash) |
| `INIT_ADMIN_PASSWORD` | Password for the seeded `admin` user |
| `INIT_USER1_PASSWORD` | Password for the seeded `trg25` user |
| `INIT_USER2_PASSWORD` | Password for the seeded `khoa12` user |
| `INIT_USER3_PASSWORD` | Password for the seeded `linh25` user |

`.env` is git-ignored. Never commit real credentials, signing keys, passwords, or
tokens.

## Running

From the repository root on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Build and test:

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd test
```

The only test is a Spring context-load smoke test, so running it requires the external
configuration above (DB, Redis, R2, initial passwords) to be available.

## Project structure

```text
src/main/java/com/example/mymangaapp/mymangaapp/
  annotation/       @RateLimit
  aop/              Rate-limit aspect
  configuration/    Startup seeding (InitApplicationConfig)
  constant/         Role name constants
  controller/       REST controllers
  dto/
    common/         ApiResponse, PaginatedResponse
    auth/           request/ and response/
    user/           request/ and response/
    manga/          request/ and response/
    chapter/        request/ and response/
    category/       request/ and response/
    transgroup/     request/ and response/
    groupcreation/  request/ and response/
    groupjoin/      response/
    role/           request/ and response/
    permission/     request/ and response/
  entity/           JPA entities
  enums/            Domain enums
  exception/        AppException, ResponseCode, GlobalExceptionHandler
  mapper/           MapStruct mappers
  repository/       Spring Data repositories
  scheduler/        View-flush and storage-cleanup jobs
  security/         JWT, filters, CORS, Redis, R2, password, rate-limit config
  service/          Business logic (incl. service/storage/)
  utils/            HTTP and storage-key helpers

src/main/resources/
  application.yaml  Runtime configuration
  rate_limit.lua    Atomic Redis fixed-window rate-limit script
```

The servlet context path is `/mymangaapp` and the server port is `8080`, so the local
base URL is:

```text
http://localhost:8080/mymangaapp
```

## API conventions

### Response wrapper

Every response uses `ApiResponse<T>`, and null fields are omitted:

```json
{
  "code": "0000",
  "message": "Thành công!",
  "result": {}
}
```

`code` defaults to `"0000"` for success. Errors use the same wrapper with a four-digit
`ResponseCode` and its mapped HTTP status. See `exception/ResponseCode.java` for the
full list; error codes are treated as a stable client contract and are not renumbered.

### Pagination

Paginated endpoints take `page` (0-based), `size`, and `sortBy` query parameters.
Services build `PageRequest.of(page, size, Sort.by(DESC, sortBy))`. Responses use
`PaginatedResponse<T>`, whose `currentPage` is one-based (Spring's zero-based index
plus one):

```json
{
  "currentPage": 1,
  "pageSize": 24,
  "totalPages": 4,
  "totalElements": 75,
  "content": []
}
```

### Authentication

The API is stateless. Send the access token on protected routes:

```http
Authorization: Bearer <access-token>
```

Public read routes are approved groups, manga, chapter/page, and category reads.
Public write routes are the `/auth/**` operations. All other routes require a token,
and `/admin/**` requires the `ADMIN` role. Business ownership rules (self-or-admin,
group leader, manga group membership) are enforced in services with `@PreAuthorize`.

### Endpoint groups

```text
POST   /auth/register    POST /auth/login       POST /auth/introspect
POST   /auth/logout      POST /auth/refresh

GET    /users/me         PATCH /users/me (multipart)     PATCH /users/me/password
GET    /admin/users      POST /admin/users      GET /admin/users/{id}
PATCH  /admin/users/{id} (multipart)             DELETE /admin/users/{id}

GET    /categories       GET  /categories/{id}
POST   /admin/categories PATCH /admin/categories/{id}    DELETE /admin/categories/{id}

GET    /mangas           GET  /mangas/{id}
POST   /mangas           PATCH /mangas/{mangaId}
DELETE /transgroups/{groupId}/mangas/{mangaId}
DELETE /mangas/{mangaId}/transgroups/{groupId}
GET    /transgroups/{groupId}/mangas
GET    /admin/mangas

GET    /mangas/{mangaId}/chapters
POST   /mangas/{mangaId}/chapters
DELETE /mangas/{mangaId}/chapters/{chapterId}
GET    /chapters/{chapterId}/pages

GET    /transgroups      GET  /transgroups/{id}
PATCH  /transgroups/{id} DELETE /transgroups/{id}
GET    /admin/transgroups
DELETE /transgroups/{groupId}/users/{userId}

POST   /transgroups/{groupId}/join-requests
GET    /transgroups/{groupId}/join-requests
PATCH  /transgroups/{groupId}/join-requests/{requestId}/approve
PATCH  /transgroups/{groupId}/join-requests/{requestId}/reject
GET    /users/me/join-requests

POST   /creation-requests
GET    /users/me/creation-requests
GET    /admin/creation-requests
PATCH  /admin/creation-requests/{requestId}/approve
PATCH  /admin/creation-requests/{requestId}/reject

POST   /admin/roles      GET /admin/roles      DELETE /admin/roles/{id}
POST   /admin/permissions GET /admin/permissions DELETE /admin/permissions/{id}

POST   /files/upload     POST /files/upload-multi   (multipart/form-data)
```

For a full request/response contract intended for frontend generation, see
[`FRONTEND_API_SPEC.md`](FRONTEND_API_SPEC.md).

## Domain model

All entities extend `BaseEntity` (audited `createdAt`/`updatedAt`). IDs are UUID
strings except `Category`, which uses an auto-increment `Long`.

- `User` — unique username/email, password, profile fields, avatar URL, optional
  membership in one `TransGroup`, and many-to-many roles.
- `Role` / `Permission` — string-named; joined through `user_roles` and
  `role_permissions`.
- `TransGroup` — leader, member users, status (`APPROVED`/`DELETED`), slug generated,
  and optional `coverImageUrl`.
  from the name, and many-to-many translated manga. The leader is not counted as a
  member.
- `Manga` — name, generated slug, author, optional `coverImageUrl`, status, description,
  many-to-many categories
  and translation groups, and one-to-many chapters (`@OrderBy chapterIndex ASC`,
  cascade + orphan removal).
- `Chapter` — unique `(manga_id, chapter_index)`, buffered `views`, one-to-many pages
  ordered by `pageNumber` (cascade + orphan removal).
- `Category` — name, description.
- `GroupCreationRequest` — user request reviewed by an admin, optionally linked to the
  created group.
- `GroupJoinRequest` — pending/approved/rejected membership request.

Enums are stored as strings and parsed case-insensitively:
`MangaStatus` = `COMPLETED` | `ONGOING` | `ONHOLD`;
`TransGroupStatus` = `APPROVED` | `DELETED`;
`GroupJoinRequestStatus` = `PENDING` | `APPROVED` | `REJECTED`;
`GroupCreationRequestStatus` = `PENDING` | `APPROVED` | `REJECTD` (note the existing
spelling).

Hibernate runs with `ddl-auto: update`, which is a development convenience, not a
production migration strategy.

## Security

- `SecutityConfig` (file/class name kept as-is) disables CSRF, enables CORS, uses
  `STATELESS` sessions, and installs `JwtAuthenticationFilter` before
  `UsernamePasswordAuthenticationFilter`.
- `JwtUtils` signs HS512 tokens with `jwt.signer-key`, stores username as subject, a
  UUID as JWT ID, and roles as a space-separated `scope` claim.
- `JwtAuthenticationFilter` verifies signature/expiry and Redis invalidation state,
  then loads authorities via `CustomUserDetailsService` (`ROLE_<name>` plus
  unprefixed permission authorities).
- `@RateLimit` with `RateLimitAspect` implements a fixed-window Redis counter keyed by
  client IP or authenticated user ID (falling back to IP). The aspect logs and
  proceeds if Redis evaluation fails (fails open).
- CORS allows `http://localhost:5173` and `http://127.0.0.1:5173` with credentials;
  override with `app.cors.allowed-origins`.

## Storage and background jobs

- Uploads are validated, optimized to WebP, and stored in R2. Temporary uploads use the
  `tmp/` prefix and are returned as public URLs; chapters copy those objects to
  `mangas/{mangaId}/{chapterId}/` and roll back the copies if the transaction fails.
- `ViewFlushScheduler.flushViews` (`*/30 * * * * *`, every 30 seconds) moves buffered
  chapter views from Redis to MySQL in batches of 500.
- `StorageCleanupScheduler.cleanupTmpFiles` (`0 0 * * * *`, hourly) deletes R2 `tmp/`
  objects older than three hours.
- `StorageCleanupScheduler.cleanupFailedUploadedFiles` (`0 0 3 * * *`, daily at 03:00)
  is scheduled but currently a placeholder.
- `InitApplicationConfig` seeds permissions, roles (`ADMIN`, `USER`, `TRANSLATOR`),
  five default categories, four users (`admin`, `trg25`, `khoa12`, `linh25`), and an
  initial admin translation group. Seeds are created only when the relevant table is
  empty.

## Development conventions

- Keep business rules and authorization in services; controllers only translate HTTP.
- Use request/response DTOs; never expose JPA entities from controllers.
- Return `ApiResponse<T>` and use `AppException` + `ResponseCode` for expected errors.
- Cover images are uploaded through `POST /files/upload` or
  `POST /files/upload-multi`; send the returned public URL as `coverImageUrl` in manga,
  group-creation, or group-update JSON. Cover fields are optional and are not stored as
  binary data in MySQL.
- Use MapStruct mappers for entity/DTO conversion.
- Prefer lazy associations, `Set` for many-to-many, and explicit `@EntityGraph` or
  purpose-built queries when related data is needed.
- Use constructor injection and `@FieldDefaults` consistently with existing code.
- Never log credentials, tokens, passwords, or signing keys.
- Do not edit generated MapStruct sources under `target/`.

For deeper contributor guidance, see [`AGENTS.md`](AGENTS.md).

## Known limitations and inconsistencies

- The README no longer describes a `manga.genres` field; the model uses the
  `manga_categories` many-to-many relation to `Category`.
- Both JJWT and Nimbus JWT dependencies are declared, but `JwtUtils` uses Nimbus; the
  JJWT declarations are unused by the current source.
- `SecutityConfig` retains its misspelled class/file name.
- `MangaResponse.transGroupsId` is a list of `TransGroupSummaryResponse`, not IDs,
  despite its name. `MangaDetailsResponse` does not include chapters; fetch them from
  `/mangas/{mangaId}/chapters`.
- The scheduled failed-upload cleanup method is a placeholder.
- Rate-limit evaluation fails open if Redis evaluation throws.
- The existing smoke test depends on a fully provisioned external environment.
