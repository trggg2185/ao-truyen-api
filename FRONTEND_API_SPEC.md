# Ảo Truyện API — Backend API Spec (for building a React frontend)

This document is the self-contained contract for **Ảo Truyện API** (`mymangaapp` REST backend).
It reflects the current backend source, including the unified DTOs (`MangaResponse`, `TransGroupResponse`)
and DTO/Interface-based projection responses.

---

## 1. Product overview

**Ảo Truyện API** is a manga-reading and translation-group platform.

- **Readers / guests** browse manga, read chapters and pages, view categories, view public user profiles,
  and view translation groups. No login required.
- **Registered users** (`USER`) sign in via Google OAuth2, update their profile/avatar, request to join a
  translation group, request to create a new translation group, track their requests, or delete their account.
- **Translators** (`TRANSLATOR`) are users who belong to a translation group; they can
  create/delete chapters for manga that their group translates (`APPROVED` status) and upload images.
- **Group leaders** are the owners of a translation group. They manage the group
  profile, approve/reject join requests, kick members, transfer leadership, remove their group from a manga,
  and create/update/delete manga for their group.
- **Admins** (`ADMIN`) manage users (including ban/unban), roles, permissions, categories, and review
  group-creation requests. Admins can also moderate groups, manga, and chapters.

---

## 2. Base URL and environment

- Local base URL: `http://localhost:8080/mymangaapp`
- CORS allows `http://localhost:5173` and `http://127.0.0.1:5173` with credentials (`allowCredentials = true`).

```env
VITE_API_BASE_URL=http://localhost:8080/mymangaapp
VITE_GOOGLE_CLIENT_ID=<same value as backend GOOGLE_CLIENT_ID>
```

---

## 3. Authentication

The API is stateless and uses **Social Login (Google ID Token)** + **short-lived JWT Access Token (in memory)** +
**rotating opaque Refresh Token (in HttpOnly cookie `refresh_token` scoped to `/mymangaapp/auth`)**.

### Endpoints

| Method | Path | RL | Body | Result |
| --- | --- | --- | --- | --- |
| POST | `/auth/social/{provider}` | `10/60 IP` | `AuthRequest { credential }` | `AuthResponse` + `Set-Cookie: refresh_token` |
| POST | `/auth/introspect` | `60/60 IP` | `IntrospectRequest { token }` | `IntrospectResponse { valid }` |
| POST | `/auth/logout` | `10/60 USER_ID` | none (header + cookie) | `Void` + clears `refresh_token` cookie |
| POST | `/auth/refresh` | `10/60 USER_ID` | none (cookie) | `AuthResponse` + rotated `refresh_token` cookie |

- Always configure Axios with `withCredentials: true` (or `fetch` with `credentials: 'include'`).
- Keep `token` (access token) in memory only.
- JWT claims: `sub` = `userId` (UUID), `jti` = token UUID, `scope` = space-separated role names (`USER`, `TRANSLATOR`, `ADMIN`), `iss` = `trg2k5.com`, `iat`, `exp`.

---

## 4. Response envelope and pagination

### `ApiResponse<T>`

```json
{
  "code": "0000",
  "message": "Thành công!",
  "result": {}
}
```

### `PaginatedResponse<T>`

```json
{
  "currentPage": 1,
  "pageSize": 24,
  "totalPages": 4,
  "totalElements": 75,
  "content": []
}
```

Request query parameters: `page` (0-based), `size`, `sortBy` (descending). Response `currentPage` is 1-based.

---

## 5. Enums

```ts
type MangaStatus = 'COMPLETED' | 'ONGOING' | 'ONHOLD';
type TransGroupStatus = 'APPROVED' | 'DELETED';
type MyGroupStatus = 'LEADER' | 'MEMBER' | 'PENDING_REQUEST' | 'NONE';
type GroupJoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
type GroupCreationRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTD';
type UserStatus = 'ACTIVE' | 'BANNED' | 'DELETED';
type RoleName = 'ADMIN' | 'USER' | 'TRANSLATOR';
```

---

## 6. DTO catalog (TypeScript)

> **Note on optimized/unified DTOs:**
> - `MangaDetailsResponse` has been removed and merged into `MangaResponse` (which includes `authorsName`, `createdAt`, `totalViews`, and `latestChapterIndex`; `updatedAt` has been removed).
> - `TransGroupDetailsResponse` has been removed and replaced by `TransGroupResponse` (which includes `avatar`, `coverImageUrl`, `leader`, `members`, `status`, `description`, `createdAt`). Group manga lists are fetched via the paginated endpoint `GET /transgroups/{groupId}/mangas`.
> - `DeleteUserResponse` has been removed (`DELETE /users/me` returns `ApiResponse<void>`).
> - All `status` fields in response DTOs use strongly-typed domain enums serialized as uppercase strings.

```ts
// ---- Common ----
interface ApiResponse<T> { code: string; message?: string; result?: T; }

interface PaginatedResponse<T> {
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  content: T[];
}

// ---- Auth ----
interface AuthRequest { credential: string; }
interface AuthResponse {
  authenticated: boolean;
  token: string;
  expiresIn: number;
}
interface IntrospectRequest { token: string; }
interface IntrospectResponse { valid: boolean; }

// ---- Users ----
interface CurrentUserResponse {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  facebook: string | null;
  discord: string | null;
  status: UserStatus;
  bio: string | null;
  avatar: string | null;
  createdAt: string;
}

interface UserSummaryResponse {
  id: string;
  username: string;
  displayName: string | null;
  avatar: string | null;
  status: UserStatus;
}

interface PublicUserProfileResponse {
  id: string;
  username: string;
  displayName: string | null;
  avatar: string | null;
  bio: string | null;
  facebook: string | null;
  discord: string | null;
  status: UserStatus;
  createdAt: string;
  transGroup: TransGroupSummaryResponse | null;
}

interface UserResponse {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  facebook: string | null;
  discord: string | null;
  avatar: string | null;
  bio: string | null;
  status: UserStatus;
  roles: RoleSummaryResponse[];
  transGroup: TransGroupSummaryResponse | null;
  createdAt: string;
  updatedAt: string;
}

interface UserUpdateForm {
  displayName?: string;
  facebook?: string;
  discord?: string;
  bio?: string;
  avatar?: File;
}

// ---- Category ----
interface CategoryRequest { name: string; description?: string; }
interface CategoryResponse { id: number; name: string; description: string | null; }
interface DeleteCategoryResponse { categoryId: number; }

// ---- Manga ----
interface MangaCreationRequest {
  name: string;
  authorsName?: string;
  coverImageUrl?: string;
  transGroups?: string[];
  categoryIds: number[];
  status: MangaStatus;
  description?: string;
}

interface MangaUpdateRequest {
  name?: string;
  authorsName?: string;
  coverImageUrl?: string;
  addTransGroups?: string[];
  categoryIds?: number[];
  status?: MangaStatus;
  description?: string;
}

// Unified response for GET /mangas, GET /mangas/recently-updated, GET /mangas/newly,
// GET /mangas/{idOrSlug}, POST /mangas, PATCH /mangas/{mangaId}
interface MangaResponse {
  id: string;
  name: string;
  slug: string;
  coverImageUrl: string | null;
  authorsName: string | null;
  status: MangaStatus;
  description: string | null;
  categories: CategoryResponse[];
  transGroups: TransGroupSummaryResponse[];
  createdAt: string;
  totalViews: number;
  latestChapterIndex: number | null;
}

interface MangaSummaryResponse {
  id: string;
  name: string;
  slug: string;
  coverImageUrl: string | null;
  authorsName: string | null;
  status: MangaStatus;
  description: string | null;
  createdAt: string;
}

interface MangaManagementResponse {
  id: string;
  name: string;
  slug: string;
  coverImageUrl: string | null;
  authorsName: string | null;
  status: MangaStatus;
  description: string | null;
  categories: CategoryResponse[];
  transGroups: TransGroupManagementResponse[];
  updatedAt: string;
}

interface TransGroupManagementResponse {
  id: string;
  name: string;
  slug: string;
  canRemove: boolean;
}

interface DeleteMangaResponse { mangaId: string; }

// ---- Chapter / Page ----
interface ChapterRequest {
  chapterIndex: number;
  title?: string;
  pageUrls: string[];
}

interface ChapterResponse {
  id: string;
  mangaName: string | null;
  chapterIndex: number;
  title: string | null;
  views: number;
  pageUrls: string[];
  updatedAt: string;
}

interface ChapterSummaryResponse {
  id: string;
  chapterIndex: number;
  title: string | null;
  views: number;
  updatedAt: string;
}

interface PageResponse {
  id: string;
  pageNumber: number;
  imageUrl: string;
}

interface DeleteChapterResponse { mangaId: string; chapterId: string; }

// ---- TransGroup ----
interface TransGroupUpdateRequest {
  name?: string;
  description?: string;
  avatarFile?: File;
  coverFile?: File;
}

interface TransferLeadershipRequest {
  newLeaderId: string;
}

interface TransGroupSummaryResponse {
  id: string;
  name: string;
  slug: string;
  avatar: string | null;
}

// Unified response for GET /transgroups/{idOrSlug}, PATCH /transgroups/{id}, and group creation approval
interface TransGroupResponse {
  id: string;
  leader: UserSummaryResponse;
  name: string;
  slug: string;
  avatar: string | null;
  coverImageUrl: string | null;
  status: TransGroupStatus;
  members: UserSummaryResponse[];
  description: string | null;
  createdAt: string;
}

interface TransGroupOfUserResponse {
  transGroup?: TransGroupSummaryResponse;
  status: MyGroupStatus;
}

interface DeleteTransGroupResponse { groupId: string; }
interface DeleteMemberResponse { groupId: string; memberId: string; }

// ---- Group creation request ----
interface CreationRequest {
  groupName: string;
  description?: string;
}

interface CreationRequestResponse {
  id: string;
  groupName: string;
  creatorName: string | null;
  status: GroupCreationRequestStatus;
  description: string | null;
  updatedAt: string;
}

// ---- Group join request ----
interface JoinRequestResponse {
  id: string;
  transGroup: TransGroupSummaryResponse;
  user: UserSummaryResponse;
  status: GroupJoinRequestStatus;
  createdAt: string;
  updatedAt: string;
}

// ---- Role / Permission ----
interface RoleRequest { name: string; description?: string; permissions: string[]; }
interface RoleSummaryResponse { name: string; description: string | null; }
interface RoleResponse { name: string; description: string | null; permissions: PermissionResponse[]; }
interface DeleteRoleResponse { roleId: string; }

interface PermissionRequest { name: string; description?: string; }
interface PermissionResponse { name: string; description: string | null; }
interface DeletePermissionResponse { permissionId: string; }
```

---

## 7. Endpoint summary

### 7.1 Auth (`/auth`)
- `POST /auth/social/{provider}` → `ApiResponse<AuthResponse>` + `Set-Cookie: refresh_token`
- `POST /auth/introspect` → `ApiResponse<IntrospectResponse>`
- `POST /auth/logout` → `ApiResponse<void>` + clears cookie
- `POST /auth/refresh` → `ApiResponse<AuthResponse>` + rotates cookie

### 7.2 Users (`/users`, `/admin/users`)
- `GET /users/{idOrUsername}` (public) → `ApiResponse<PublicUserProfileResponse>`
- `GET /users/me` → `ApiResponse<CurrentUserResponse>`
- `PATCH /users/me` (multipart) → `ApiResponse<CurrentUserResponse>`
- `DELETE /users/me` → `ApiResponse<void>`
- `GET /admin/users?page=0&size=20&sortBy=createdAt` → `ApiResponse<PaginatedResponse<UserSummaryResponse>>`
- `GET /admin/users/{id}` → `ApiResponse<UserResponse>`
- `PATCH /admin/users/{id}` (multipart) → `ApiResponse<UserResponse>`
- `PATCH /admin/users/{userId}/ban` → `ApiResponse<void>`
- `PATCH /admin/users/{userId}/unban` → `ApiResponse<void>`

### 7.3 Categories (`/categories`, `/admin/categories`)
- `GET /categories` (public) → `ApiResponse<CategoryResponse[]>`
- `GET /categories/{id}` (public) → `ApiResponse<CategoryResponse>`
- `POST /admin/categories` → `ApiResponse<CategoryResponse>`
- `PATCH /admin/categories/{id}` → `ApiResponse<CategoryResponse>`
- `DELETE /admin/categories/{id}` → `ApiResponse<DeleteCategoryResponse>`

### 7.4 Manga (`/mangas`, `/admin/mangas`, `/transgroups/{groupId}/mangas`)
- `GET /mangas?page=0&size=24&sortBy=createdAt` (public) → `ApiResponse<PaginatedResponse<MangaResponse>>`
- `GET /mangas/recently-updated` (public, top 20 ordered by `latestChapterAt DESC`) → `ApiResponse<MangaResponse[]>`
- `GET /mangas/newly` (public, top 10 newly created manga that have at least 1 chapter) → `ApiResponse<MangaResponse[]>`
- `GET /mangas/{idOrSlug}` (public) → `ApiResponse<MangaResponse>`
- `GET /transgroups/{groupId}/mangas?page=0&size=15&sortBy=createdAt` (public) → `ApiResponse<PaginatedResponse<MangaSummaryResponse>>`
- `GET /mangas/{mangaId}/management` (leader) → `ApiResponse<MangaManagementResponse>`
- `POST /mangas` (leader) → `ApiResponse<MangaResponse>`
- `PATCH /mangas/{mangaId}` (leader) → `ApiResponse<MangaResponse>`
- `DELETE /mangas/{mangaId}` (leader/admin) → `ApiResponse<DeleteMangaResponse>`
- `DELETE /mangas/{mangaId}/transgroups/{groupId}` (leader leaves manga) → `ApiResponse<void>`
- `GET /admin/mangas?status=&page=0&size=24&sortBy=createdAt` (admin) → `ApiResponse<PaginatedResponse<MangaSummaryResponse>>`

### 7.5 Chapters & Pages (`/mangas/{mangaId}/chapters`, `/chapters/{chapterId}/pages`)
- `GET /mangas/{mangaId}/chapters?page=0&size=20&sortBy=chapterIndex` (public) → `ApiResponse<PaginatedResponse<ChapterSummaryResponse>>`
- `POST /mangas/{mangaId}/chapters` (member/leader/admin) → `ApiResponse<ChapterResponse>`
- `DELETE /mangas/{mangaId}/chapters/{chapterId}` (member/leader/admin) → `ApiResponse<DeleteChapterResponse>`
- `GET /chapters/{chapterId}/pages?sortBy=pageNumber` (public) → `ApiResponse<PageResponse[]>`

### 7.6 Translation Groups (`/transgroups`, `/admin/transgroups`)
- `GET /users/me/transgroups` → `ApiResponse<TransGroupOfUserResponse>`
- `GET /transgroups?page=0&size=20&sortBy=createdAt` (public) → `ApiResponse<PaginatedResponse<TransGroupSummaryResponse>>`
- `GET /transgroups/{idOrSlug}` (public) → `ApiResponse<TransGroupResponse>`
- `PATCH /transgroups/{id}` (multipart, leader/admin) → `ApiResponse<TransGroupResponse>`
- `DELETE /transgroups/{id}` (leader/admin) → `ApiResponse<DeleteTransGroupResponse>`
- `GET /admin/transgroups?status=&page=0&size=20&sortBy=createdAt` (admin) → `ApiResponse<PaginatedResponse<TransGroupSummaryResponse>>`
- `DELETE /transgroups/{groupId}/users/{userId}` (leader kicks member) → `ApiResponse<DeleteMemberResponse>`
- `PATCH /transgroups/{groupId}/leader` (leader/admin transfers leadership) → `ApiResponse<void>`

### 7.7 Join Requests & Creation Requests
- `POST /transgroups/{groupId}/join-requests` → `ApiResponse<JoinRequestResponse>`
- `GET /users/me/join-requests?status=` → `ApiResponse<JoinRequestResponse[]>`
- `GET /transgroups/{groupId}/join-requests?status=` (leader) → `ApiResponse<JoinRequestResponse[]>`
- `PATCH /transgroups/{groupId}/join-requests/{requestId}/approve` (leader) → `ApiResponse<JoinRequestResponse>`
- `PATCH /transgroups/{groupId}/join-requests/{requestId}/reject` (leader) → `ApiResponse<JoinRequestResponse>`
- `POST /creation-requests` → `ApiResponse<CreationRequestResponse>`
- `GET /users/me/creation-requests?status=&page=0&size=20&sortBy=createdAt` → `ApiResponse<PaginatedResponse<CreationRequestResponse>>`
- `GET /admin/creation-requests?status=&page=0&size=20&sortBy=createdAt` (admin) → `ApiResponse<PaginatedResponse<CreationRequestResponse>>`
- `PATCH /admin/creation-requests/{requestId}/approve` (admin) → `ApiResponse<TransGroupResponse>`
- `PATCH /admin/creation-requests/{requestId}/reject` (admin) → `ApiResponse<CreationRequestResponse>`

### 7.8 Roles, Permissions & File Upload
- `POST /admin/roles`, `GET /admin/roles`, `DELETE /admin/roles/{id}`
- `POST /admin/permissions`, `GET /admin/permissions`, `DELETE /admin/permissions/{id}`
- `POST /files/upload` (multipart `file`), `POST /files/upload-multi` (multipart `files`)
