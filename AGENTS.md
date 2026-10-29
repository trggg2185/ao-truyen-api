# AGENTS.md

Project-specific guidance for contributors and coding agents working on **Ảo Truyện API** (`mymangaapp`).
This document is based on the current source tree, `pom.xml`, configuration, and tests.

## Project overview

**Ảo Truyện API** (`mymangaapp`) is a REST backend for a manga-reading and translation-group platform. The
application supports social authentication (Google OAuth2 ID Token + rotating HttpOnly refresh cookies),
users and roles, translation groups, manga, chapters/pages, categories, WebP image processing and Cloudflare R2
uploads, and group creation/join workflows.

The Maven coordinates are `com.example.mymangaapp:mymangaapp:0.0.1-SNAPSHOT`.
The Java package root is:

```text
com.example.mymangaapp.mymangaapp
```

The HTTP context path is `/mymangaapp` and the default server port is `8080`.

## Tech stack and versions

- Java 21.
- Spring Boot `3.5.14`.
- Spring MVC/Web, Validation, Actuator, Data JPA, Data Redis, Security, and AOP.
- MySQL Connector/J at runtime.
- Hibernate/Jakarta Persistence through Spring Data JPA.
- JWT & Social Auth:
  - Nimbus JOSE JWT `10.5` is used by `JwtUtils` for HS512 access-token signing and verification.
  - Google API Client `2.9.1` (`GoogleIdTokenVerifier`) is used for verifying Google ID tokens.
  - Opaque SHA-256 hashed refresh tokens with family rotation in Redis (`refresh_token.lua`) and HttpOnly cookies.
- MapStruct `1.6.3` for entity/DTO mapping on write paths.
- Lombok `1.18.38`, with `lombok-mapstruct-binding` `0.2.0`.
- AWS SDK S3 `2.29.15`, configured for Cloudflare R2.
- Redis for invalidated access tokens, fixed-window rate limits, refresh-token rotation families, and buffered chapter views.
- Slugify `3.0.7` + ICU4J `75.1` for Vietnamese transliteration and manga/group slugs.
- Thumbnailator `0.4.20` and WebP ImageIO `0.1.6` for image processing.
- Maven Wrapper (`mvnw`/`mvnw.cmd`) is checked in; use it instead of assuming a global
  Maven installation.

## Repository layout

```text
src/main/java/com/example/mymangaapp/mymangaapp/
  annotation/       Custom annotations such as @RateLimit
  aop/              Rate-limit aspect
  configuration/    Startup data initialization, async/MDC config
  constant/         Shared constants (RoleConstants, SequenceCounterConstants)
  controller/       REST endpoints
  dto/
    common/         Shared response envelope (ApiResponse) and pagination DTOs (PaginatedResponse)
    auth/           Authentication request and response DTOs
    user/           User request and response DTOs
    manga/          Manga request and response DTOs
    chapter/        Chapter and page request/response DTOs
    category/       Category request and response DTOs
    transgroup/     Translation-group request and response DTOs
    groupcreation/  Group-creation request and response DTOs
    groupjoin/      Group-join response DTOs
    role/           Role request and response DTOs
    permission/     Permission request and response DTOs
  entity/           JPA entities and relationships
  enums/            Persisted/domain enums
  exception/        AppException, response codes, global error handling
  filter/           API request logging filter
  mapper/           MapStruct mapper interfaces
  repository/       Spring Data JPA repositories, DTO projections, and custom queries
    projection/     Interface-based projections (MangaCategoryProjection, MangaTransGroupProjection)
  scheduler/        Scheduled Redis view-flush and R2 cleanup jobs
  security/
    component/      Method-security helper beans (chapterSec, groupSec, mangaSec, userSec)
    config/         Security, CORS, Redis, R2, refresh-token, rate-limit config
    filter/         JWT request filter
    jwt/            Authentication entry point, AuthTokens, RefreshCookieFactory
    service/        UserDetails adapter/service
    social/         Social identity verifier SPI and Google verifier
    utils/          JWT and security context helpers
  service/          Business/application services (including auth/ and storage/)
  utils/            HTTP, Slug, StorageKeys, UsernameGenerator, UUID helpers

src/main/resources/
  application.yaml  Runtime configuration and environment placeholders
  rate_limit.lua    Atomic fixed-window Redis rate-limit script
  refresh_token.lua Atomic Redis refresh-token rotation & grace-period script
```

Generated output under `target/` is build output, not source. Do not edit generated
MapStruct implementations or compiled classes.

## Domain model and relationships

All entities extend `BaseEntity`, which provides `createdAt` and `updatedAt` auditing
columns. Auditing is enabled on the application class with `@EnableJpaAuditing`.

- `User`
  - UUID string identifier, unique username and email, profile fields (`displayName`, `bio`, `facebook`, `discord`, `avatar`), `status` (`ACTIVE`, `BANNED`, `DELETED`).
  - Many-to-one membership in a `TransGroup`; a user can lead groups (`OneToMany` `transGroups`, only one `APPROVED` at a time).
  - Many-to-many roles through `user_roles`.
  - One-to-many `UserIdentity` rows linking external OAuth providers (`GOOGLE`, `FACEBOOK`, `DISCORD`).
- `UserIdentity`
  - UUID string identifier, `user`, `provider`, `providerUserId` (`sub`), and reference `email`.
- `Role`
  - String name as primary key; many-to-many users and permissions through `role_permissions`.
- `Permission`
  - String name as primary key; many-to-many roles.
- `TransGroup`
  - UUID string identifier, leader (`User`), member users, `name`, unique `slug`, `avatar`, `coverImageUrl`, `description`, `status` (`APPROVED`, `DELETED`), and translated manga.
  - `@PrePersist`/`@PreUpdate` generates the slug from the name.
- `Manga`
  - UUID string identifier, `name`, unique `slug` (`name-sequenceNumber`), `sequenceNumber`, `authorsName`, `coverImageUrl`, `description`, `status` (`ONGOING`, `COMPLETED`, `ONHOLD`), `latestChapterAt` (`Instant`), `latestChapterIndex` (`Integer`), and buffered `totalViews` (`Long`, sum of chapter views).
  - Many-to-many categories and translation groups.
  - One-to-many chapters with cascade/orphan removal and ascending `chapterIndex`.
- `SequenceCounter`
  - String `name` primary key (`MANGA`, `TRANSGROUP`) with `nextValue` locked via `PESSIMISTIC_WRITE` when generating sequential slugs.
- `Category`
  - Auto-increment `Long` identifier; many-to-many manga.
- `Chapter`
  - UUID string identifier, manga, `chapterIndex`, `title`, and buffered `views` count.
  - Unique `(manga_id, chapter_index)`.
  - One-to-many pages with cascade/orphan removal and ascending `pageNumber`.
- `Page`
  - UUID string identifier, chapter, `pageNumber`, and `imageUrl`.
  - Unique `(chapter_id, page_number)`.
- `GroupCreationRequest`
  - User-submitted request (`PENDING`, `APPROVED`, `REJECTD`) reviewed by an admin; links to the created group when approved.
- `GroupJoinRequest`
  - User request to join a group, with `PENDING`/`APPROVED`/`REJECTED` status and a
    unique `(transgroup_id, user_id, status)` constraint.

Enums are stored as strings with `@Enumerated(EnumType.STRING)`.

## API and application-layer patterns

### Controllers

Controllers are in `controller/` and return `ApiResponse<T>` rather than returning
entities directly. Main endpoint areas are:

- `/auth`: social login (`/auth/social/{provider}`), introspect, logout, refresh.
- `/users` and `/admin/users`: public user profile (`/users/{idOrUsername}`), current-user profile/account deletion (`/users/me`), and admin user management/ban/unban.
- `/mangas`, `/transgroups/{groupId}/mangas`, and `/admin/mangas`.
- `/mangas/{mangaId}/chapters` and `/chapters/{chapterId}/pages`.
- `/transgroups`, `/users/me/transgroups`, group updates/soft-deletion, leadership transfer, member kick, and join requests.
- `/creation-requests`, `/users/me/creation-requests`, and `/admin/creation-requests`.
- `/categories` and `/admin/categories`.
- `/admin/roles` and `/admin/permissions`.
- `/files/upload` and `/files/upload-multi`.

### DTO Organization (Cleaned & Optimized)

Redundant response DTOs (`MangaDetailsResponse`, `TransGroupDetailsResponse`, `DeleteUserResponse`)
have been removed and consolidated:

```text
dto/
  common/
    ApiResponse.java
    PaginatedResponse.java
  auth/
    request/      AuthRequest, IntrospectRequest
    response/     AuthResponse, IntrospectResponse
  user/
    request/      UserUpdateRequest
    response/     CurrentUserResponse, PublicUserProfileResponse,
                  UserResponse, UserSummaryResponse
  manga/
    request/      MangaCreationRequest, MangaUpdateRequest
    response/     MangaResponse, MangaSummaryResponse,
                  MangaManagementResponse, DeleteMangaResponse
  chapter/
    request/      ChapterRequest
    response/     ChapterResponse, ChapterSummaryResponse, PageResponse,
                  DeleteChapterResponse
  category/
    request/      CategoryRequest
    response/     CategoryResponse, DeleteCategoryResponse
  transgroup/
    request/      TransGroupUpdateRequest, TransferLeadershipRequest
    response/     TransGroupResponse, TransGroupSummaryResponse,
                  TransGroupManagementResponse, TransGroupOfUserResponse,
                  DeleteTransGroupResponse, DeleteMemberResponse
  groupcreation/
    request/      CreationRequest
    response/     CreationRequestResponse
  groupjoin/
    response/     JoinRequestResponse
  role/
    request/      RoleRequest
    response/     RoleResponse, RoleSummaryResponse, DeleteRoleResponse
  permission/
    request/      PermissionRequest
    response/     PermissionResponse, DeletePermissionResponse
```

- `MangaResponse` is used for public manga list (`GET /mangas`), home lists (`GET /mangas/recently-updated` and `GET /mangas/newly`), manga details (`GET /mangas/{idOrSlug}`), manga creation (`POST /mangas`), and manga updates (`PATCH /mangas/{mangaId}`). It exposes `totalViews` and `latestChapterIndex` instead of `updatedAt`.
- `TransGroupResponse` is used for group detail (`GET /transgroups/{idOrSlug}`), group creation approval, and group updates (`PATCH /transgroups/{id}`). A group's translated mangas are fetched via the dedicated paginated endpoint `GET /transgroups/{groupId}/mangas` (`PaginatedResponse<MangaSummaryResponse>`), not embedded unpaginated inside the group detail response.

### Repository Query & Projection Strategy (DTO-based & Interface-based Projections)

All read-only endpoints use **DTO-based Projections (`SELECT new ...`)** or **Interface-based Projections**
instead of loading full JPA entities and mapping them with MapStruct:

1. **DTO-based Projection (`SELECT new com.example...Dto(...)`)**:
   - Used when the query maps directly to a response DTO:
     - `CategoryRepository`: `findAllResponses()`, `findResponseById(id)` -> `CategoryResponse`
     - `ChapterRepository`: `findSummariesByMangaId(mangaId, pageable)` -> `Page<ChapterSummaryResponse>`
     - `PageRepository`: `findAllResponsesByChapterId(chapterId, sort)` -> `List<PageResponse>`
     - `PermissionRepository`: `findAllResponses()` -> `List<PermissionResponse>`
     - `GroupCreationRequestRepository`: `findResponses(creatorId, status, pageable)` -> `Page<CreationRequestResponse>`
     - `GroupJoinRequestRepository`: `findResponses(userId, groupId, status)` -> `List<JoinRequestResponse>` (using flat constructor to assemble nested `TransGroupSummaryResponse` and `UserSummaryResponse`)
     - `TransGroupRepository`: `findSummaries(status, pageable)`, `findMemberGroupSummary(userId, status)`, `findSummaryByLeaderIdAndStatus(leaderId, status)` -> `TransGroupSummaryResponse`
     - `UserRepository`: `findCurrentUserByUsername(username)` -> `CurrentUserResponse`, `findPublicProfileById/ByUsername` -> `PublicUserProfileResponse`, `findAllSummaries(pageable)` -> `Page<UserSummaryResponse>`
     - `MangaRepository`: `findPublicPage(pageable)`, `findRecentlyUpdatedMangas(pageable)`, `findNewlyMangas(pageable)`, `findProjectedById(id)`, `findProjectedBySlug(slug)` -> `MangaResponse`; `findManagementById(id)` -> `MangaManagementResponse`; `findSummaries(status, pageable)`, `findSummariesByGroupId(groupId, pageable)` -> `Page<MangaSummaryResponse>`
2. **Interface-based Projection (`repository/projection/`)**:
   - Used for batch-loading child collections (`categories` and `transGroups`) across multiple `mangaIds` (`MangaCategoryProjection` and `MangaTransGroupProjection`).
   - Why? Using `@EntityGraph(attributePaths = {"categories", "transGroups"})` on `Page<Manga>` causes Hibernate `HHH90003004` (in-memory pagination of the entire table!) and a Cartesian product between `categories` and `transGroups`. Fetching the scalar page via DTO projection + 2 batch `WHERE m.id IN :mangaIds` queries via interface projections keeps SQL `LIMIT/OFFSET` in MySQL and runs in exactly 3 flat queries.
3. **Entity loading (`@EntityGraph`)**:
   - Reserved for write/mutation flows that modify managed entities (`updateMangaById`, `approveJoinGroup`, `softDeleteGroupById`, etc.) or bounded non-paginated graphs (`RoleRepository.findAll()`, `TransGroupRepository.findWithDetailsById/Slug`).

## Security

- `SecutityConfig` enables web security and method security, disables CSRF for the
  stateless API, enables CORS, sets `SessionCreationPolicy.STATELESS`, and installs
  `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
- Public POST routes are `/auth/social/*`, `/auth/introspect`, `/auth/logout`, `/auth/refresh`.
- Public GET routes cover approved/public group, manga, chapter/page, category, and public user profile reads.
- `/admin/**` requires `hasRole("ADMIN")`.
- `JwtUtils` signs HS512 access tokens using `app.auth.signer-key`, stores `user.id` as subject (`sub`),
  UUID as JWT ID (`jti`), and roles as a space-separated `scope` claim.
- Refresh tokens are 256-bit random opaque tokens stored in HttpOnly cookies (`/mymangaapp/auth`) and tracked as SHA-256 hashes in Redis with family rotation and grace window (`refresh_token.lua`).

## Scheduled jobs and background behavior

- `ViewFlushScheduler.flushViews`: every 5 minutes (`0 */5 * * * *`), claims buffered chapter view increments from Redis using atomic `RENAMENX`, updates `chapter.views` and aggregates/updates parent `manga.total_views` in the same transaction per batch of 500 using `JdbcTemplate`, then deletes flushed keys from Redis.
- `StorageCleanupScheduler.cleanupTmpFiles`: every hour (`0 0 * * * *`), deletes R2 objects under `tmp/` older than 3 hours.
- `StorageCleanup`: registers `TransactionSynchronization` hooks to delete promoted R2 files if a DB transaction rolls back, or delete chapter/manga/user R2 prefixes asynchronously after a DB delete transaction commits.

## Common tasks

Use the Maven Wrapper from the repository root:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```
