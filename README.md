# Ảo Truyện API (`mymangaapp`)

Backend REST API cho nền tảng đọc truyện tranh (Manga) và quản lý nhóm dịch (**Ảo Truyện API**).
Dự án cho phép độc giả tìm kiếm, đọc truyện theo chương/trang, khám phá các nhóm dịch; hỗ trợ người dùng đăng nhập qua Google OAuth2, xin gia nhập hoặc đăng ký thành lập nhóm dịch; đồng thời cung cấp bộ công cụ quản trị cho Leader nhóm dịch và Admin.

## Tính năng chính

- **Xác thực hiện đại (OAuth2 + JWT + Rotating Refresh Cookie)**:
  - Đăng nhập/Đăng ký tự động qua Google ID Token (`POST /auth/social/google`), liên kết đa danh tính qua bảng `user_identity`.
  - Access Token dạng JWT (HS512, ngắn hạn, lưu trên RAM frontend) kết hợp Refresh Token dạng chuỗi ngẫu nhiên 256-bit (băm SHA-256 lưu Redis, xoay vòng nguyên tử bằng Lua script `refresh_token.lua` và gắn vào `HttpOnly` cookie).
- **Phân quyền (RBAC + Method Security)**:
  - Phân quyền theo Role (`ADMIN`, `USER`, `TRANSLATOR`) và Permission (`CREATE_POST`, `CREATE_MANGA`, `UPDATE_MANGA`), kết hợp kiểm tra quyền sở hữu ở tầng Service (`@PreAuthorize` với `mangaSec`, `groupSec`, `chapterSec`, `userSec`).
- **Quản lý Nhóm dịch (Translation Groups)**:
  - Quy trình gửi yêu cầu tạo nhóm dịch (Admin duyệt), xin gia nhập nhóm dịch (Leader duyệt), trục xuất thành viên (kick), chuyển nhượng quyền Leader, xoá mềm nhóm dịch (kiểm tra truyện mồ côi trước khi xoá).
- **Quản lý Truyện, Thể loại, Chương và Trang ảnh**:
  - Hỗ trợ nhiều nhóm cùng dịch một bộ truyện, sinh `slug` chuẩn SEO Tiếng Việt kèm bộ đếm tuần tự (`SequenceCounter` có khoá `PESSIMISTIC_WRITE`).
  - Truy vấn đọc (Read paths) được tối ưu hoá toàn diện bằng **DTO-based Projection (`SELECT new ...`)** và **Interface-based Projection**, loại bỏ triệt để lỗi N+1 query, Cartesian Product và lỗi phân trang trên RAM (`HHH90003004`).
- **Xử lý ảnh & Lưu trữ đám mây (Cloudflare R2)**:
  - Kiểm tra định dạng, chống ảnh quá khổ (Decompression Bomb protection `MAX_PIXELS`), tự động cắt tỉa/nén sang định dạng `WebP` bằng `Thumbnailator` + `webp-imageio`.
  - Cơ chế upload tạm (`tmp/`) -> promote sang thư mục chính thức khi tạo truyện/chương, tự động xoá file rác trên R2 nếu DB transaction rollback hoặc sau khi xoá truyện/chương/tài khoản (`StorageCleanup`).
- **Redis Caching, Rate Limiting & Buffered Views**:
  - Chặn spam API bằng Fixed-Window Rate Limit chạy Lua script nguyên tử (`rate_limit.lua`) theo `IP` hoặc `USER_ID`.
  - Đệm lượt xem chương truyện (chống spam view 30 phút/người đọc) trên Redis và định kỳ đồng bộ (batch flush) xuống MySQL mỗi 5 phút bằng `JdbcTemplate` (`ViewFlushScheduler`).

## Công nghệ sử dụng (Tech Stack)

- **Ngôn ngữ & Framework**: Java 21, Spring Boot 3.5.14 (Web, Validation, Security, Data JPA, Data Redis, AOP, Actuator)
- **Cơ sở dữ liệu**: MySQL, Redis
- **Bảo mật & Xác thực**: Nimbus JOSE JWT 10.5, Google API Client 2.9.1
- **Object Mapping & Boilerplate**: MapStruct 1.6.3, Lombok 1.18.38
- **Lưu trữ & Xử lý ảnh**: AWS SDK S3 2.29.15 (Cloudflare R2), Thumbnailator 0.4.20, WebP ImageIO 0.1.6
- **Tiện ích**: Slugify 3.0.7 + ICU4J 75.1
- **Build Tool**: Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Cấu hình biến môi trường (`.env`)

Sao chép `.env.example` thành `.env` hoặc thiết lập các biến môi trường sau trước khi chạy:

| Biến môi trường | Mô tả |
| --- | --- |
| `DB_URL` | JDBC URL kết nối MySQL |
| `DB_PASSWORD` | Mật khẩu MySQL |
| `REDIS_HOST` | Host của Redis (mặc định `localhost`) |
| `REDIS_PORT` | Cổng Redis (mặc định `6379`) |
| `REDIS_PASSWORD` | Mật khẩu Redis (mặc định rỗng) |
| `REDIS_DATABASE` | Chỉ số DB Redis (mặc định `0`) |
| `ADMIN_EMAIL` | Email Google được tự động cấp quyền `ADMIN` khi đăng nhập lần đầu |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID |
| `SIGNER_KEY` | Khóa bí mật ký JWT HS512 (tối thiểu 64 bytes) |
| `ACCESS_TTL` | Thời gian sống của Access Token (VD: `15m`) |
| `REFRESH_TTL` | Thời gian sống của Refresh Token (VD: `7d`) |
| `REFRESH_GRACE` | Khoảng thời gian ân hạn khi xoay vòng Refresh Token (VD: `10s`) |
| `COOKIE_SECURE` | Bật `Secure` cho cookie (`true` trên HTTPS production, `false` ở local) |
| `CLOUDFLARE_R2_ACCESS_KEY_ID` | Access Key của Cloudflare R2 |
| `CLOUDFLARE_R2_SECRET_ACCESS_KEY` | Secret Key của Cloudflare R2 |
| `CLOUDFLARE_R2_ENDPOINT_URL` | S3 Endpoint URL của R2 |
| `CLOUDFLARE_R2_BUCKET_NAME` | Tên R2 Bucket |
| `CLOUDFLARE_R2_PUBLIC_URL` | Tên miền CDN công khai của R2 (không có dấu `/` ở cuối) |

## Cách chạy dự án

Từ thư mục gốc của dự án:

```powershell
# Chạy ứng dụng ở môi trường phát triển
.\mvnw.cmd spring-boot:run

# Biên dịch kiểm tra lỗi
.\mvnw.cmd clean compile

# Đóng gói file JAR
.\mvnw.cmd clean package
```

Base URL mặc định tại local: `http://localhost:8080/mymangaapp`

## Cấu trúc thư mục

```text
src/main/java/com/example/mymangaapp/mymangaapp/
  annotation/       @RateLimit
  aop/              RateLimitAspect
  configuration/    InitApplicationConfig, AsyncConfig, MdcTaskDecorator
  constant/         RoleConstants, SequenceCounterConstants
  controller/       REST Controllers
  dto/              Request/Response DTOs theo từng module
  entity/           JPA Entities (kế thừa BaseEntity)
  enums/            Domain Enums
  exception/        AppException, ResponseCode, GlobalExceptionHandler
  filter/           ApiLoggingFilter
  mapper/           MapStruct Mappers
  repository/       Spring Data JPA Repositories & DTO Projections
    projection/     Interface-based Projections (MangaCategoryProjection, MangaTransGroupProjection)
  scheduler/        ViewFlushScheduler, StorageCleanupScheduler
  security/         JWT, OAuth2 Google Verifier, Cookie, Security & Redis/R2 Configs
  service/          Business Logic Services (bao gồm auth/ và storage/)
  utils/            HttpUtils, SlugUtils, StorageKeysUtils, UsernameGenerator, UUIDUtils
```

## Tài liệu liên quan trong kho mã nguồn

- [`FRONTEND_API_SPEC.md`](FRONTEND_API_SPEC.md): Đặc tả chi tiết toàn bộ REST API, DTO TypeScript và luồng xác thực dành cho Frontend.
- [`AGENTS.md`](AGENTS.md): Quy chuẩn kiến trúc, quy tắc DTO/Projection và hướng dẫn dành cho lập trình viên / AI coding agent.
- [`PRODUCTION_READINESS_GUIDE.md`](PRODUCTION_READINESS_GUIDE.md): Phân tích chi tiết các điểm cần sửa trong code hiện tại, các tính năng cần bổ sung cho giai đoạn MVP Production, và hướng dẫn triển khai từng bước dễ bảo trì, mở rộng.
