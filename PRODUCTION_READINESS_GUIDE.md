# Hướng Dẫn Hoàn Thiện Production & Kiến Trúc Mở Rộng — Ảo Truyện API

Tài liệu này được biên soạn riêng cho bạn (sinh viên năm 4 đang hoàn thiện dự án **Ảo Truyện API** để học sâu bản chất và chuẩn bị phỏng vấn thực tập/Fresher Java Backend). Nội dung gồm 4 phần:

1. **Tổng kết những tối ưu DTO & Projection vừa thực hiện trong dự án** (để bạn tự tin giải thích khi phỏng vấn).
2. **Chi tiết các lỗi tiềm ẩn / điểm chưa ổn trong code hiện tại cần sửa ngay** (kèm đoạn code sửa cụ thể).
3. **Các chức năng cốt lõi cần bổ sung cho giai đoạn MVP Production** (kèm thiết kế bảng, kiến trúc và code mẫu).
4. **Cẩm nang trả lời phỏng vấn thực tập từ chính dự án Ảo Truyện API**.

---

## Phần 1: Tổng Kết Tối Ưu Response & Repository Projection Đã Thực Hiện

### 1.1. Dọn dẹp và hợp nhất các Response DTO dư thừa
- **Xoá `MangaDetailsResponse` → Hợp nhất vào `MangaResponse`**:
  - Trước đây `MangaResponse` (dùng cho `GET /mangas`) và `MangaDetailsResponse` (dùng cho `GET /mangas/{idOrSlug}`) giống nhau tới 90%, chỉ lệch nhau trường `authorsName`, `createdAt` và `updatedAt`. Cả hai đều không chứa danh sách `chapters` (vì chương đã tách ra `GET /mangas/{mangaId}/chapters`).
  - Việc gộp vào `MangaResponse` giúp Frontend dùng chung 1 kiểu dữ liệu duy nhất cho cả danh sách, chi tiết, tạo mới và cập nhật truyện.
- **Xoá `TransGroupDetailsResponse` → Thay bằng `TransGroupResponse`**:
  - `TransGroupDetailsResponse` trước đây bị thiếu trường `avatar` (trong khi `TransGroupResponse` có đủ) và lại nhồi toàn bộ `Set<MangaSummaryResponse> translatedMangas` **không phân trang** vào trong response chi tiết nhóm — dù dự án đã có sẵn endpoint phân trang `GET /transgroups/{groupId}/mangas`. Nếu 1 nhóm dịch 500 bộ truyện, API cũ sẽ kéo cả 500 bộ lên RAM mỗi lần khách ghé thăm trang nhóm.
- **Xoá `DeleteUserResponse` và `PageMapper`**:
  - `DeleteUserResponse` không được sử dụng ở bất kỳ đâu (`DELETE /users/me` trả về `ApiResponse<Void>`).
  - `PageMapper` không còn cần thiết vì `PageRepository` đã trả thẳng `List<PageResponse>` từ câu truy vấn JPQL.

### 1.2. Bản chất của DTO-based Projection vs Interface-based Projection (Kiến thức đi phỏng vấn)

Khi bạn gọi `repository.findAll()` thông thường để trả dữ liệu cho các API `GET`:
1. Hibernate `SELECT` **tất cả các cột** của Entity.
2. Khởi tạo đối tượng Entity, lưu bản chụp (snapshot) vào **Persistence Context (1st-level cache)** để cuối transaction kiểm tra xem có trường nào bị sửa không (**Dirty Checking**).
3. MapStruct lại duyệt từng Entity để copy sang đối tượng DTO mới.

Với các thao tác **chỉ đọc (`@Transactional(readOnly = true)`)**, toàn bộ bước 2 và 3 là lãng phí CPU và RAM! Giải pháp là dùng **Projection**:

| Tiêu chí | DTO-based Projection (`SELECT new ...Dto(...)`) | Interface-based Projection (`interface XxxProjection`) |
| --- | --- | --- |
| **Cách viết** | Dùng từ khoá `SELECT new com.example...Dto(e.col1, e.col2)` trong `@Query`, gọi trực tiếp constructor của class DTO. | Khai báo `interface` chỉ gồm các hàm `getXxx()`. Trong `@Query`, đặt alias `AS xxx` trùng tên với `getXxx()`. |
| **Cơ chế bên dưới** | Hibernate đọc `ResultSet` của JDBC và gọi thẳng `new Dto(...)`. Không tạo Entity, không tạo Proxy. | Spring Data JPA dùng **JDK Dynamic Proxy** bọc lấy mảng `Tuple/Object[]` trả về từ Hibernate. |
| **Khi nào nên dùng?** | Khi kết quả câu query **trả thẳng về cho Client** (API Response DTO như `MangaSummaryResponse`, `PageResponse`, `CategoryResponse`, `UserSummaryResponse`...). | Khi kết quả câu query là **dữ liệu trung gian nội bộ** ở tầng Service (ví dụ: cần lấy kèm khoá ngoại `mangaId` để `groupingBy` trong `MangaCategoryProjection`, `MangaTransGroupProjection`) mà không muốn tạo thêm class DTO rác. |

### 1.3. Cách giải quyết bài toán kinh điển: Phân trang (`Pageable`) + Quan hệ `ManyToMany` trong `MangaRepository`
- **Lỗi cũ**: Trên hàm `Page<Manga> findAll(Pageable)`, việc gắn `@EntityGraph(attributePaths = {"categories", "transGroups"})` gây ra **2 thảm hoạ hiệu năng**:
  1. Cảnh báo `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory!`: Vì SQL `LEFT JOIN` với bảng 1-N / N-N làm nhân bản số dòng của 1 bộ truyện, MySQL không thể cắt `LIMIT/OFFSET` chính xác. Hibernate đành **bỏ `LIMIT/OFFSET` trong câu SQL, kéo TOÀN BỘ bảng `manga` dưới DB lên RAM Java** rồi mới cắt 24 phần tử!
  2. **Tích Đề-các (Cartesian Product)**: `LEFT JOIN` cùng lúc 2 tập hợp (`categories` có $M$ phần tử và `transGroups` có $N$ phần tử) tạo ra $M \times N$ dòng dữ liệu trùng lặp cho mỗi bộ truyện.
- **Cách đã sửa trong `MangaRepository` + `MangaService`**:
  - **Query 1 (DTO-based Projection)**: `findPublicPage(pageable)` chỉ `SELECT new MangaResponse(...)` trên bảng `manga`. Câu SQL có `LIMIT 24 OFFSET 0` chuẩn 100% dưới MySQL.
  - **Query 2 & 3 (Interface-based Projection)**: Lấy 24 `mangaId` vừa tìm được, gọi `findCategoryProjectionsByMangaIds(mangaIds)` và `findTransGroupProjectionsByMangaIds(mangaIds)` (`WHERE m.id IN (...)`), sau đó gom nhóm bằng `Collectors.groupingBy` và gán vào `MangaResponse`.
  - **Kết quả**: Luôn cố định **3 câu SQL đơn giản**, nhanh gấp hàng chục lần khi dữ liệu lớn.

---

## Phần 2: Những Điểm Trong Dự Án Cần Sửa Lại NgayTrước Khi Lên Production

Dưới đây là 9 lỗi/vấn đề thực tế đang tồn tại trong source code hiện tại mà bạn nên sửa:

### 2.1. [NGHIÊM TRỌNG - Bảo mật] Đang in Access Token và Refresh Token ra Log (`AuthController.java`)
- **Vị trí**: `src/main/java/.../controller/AuthController.java` (dòng 60–64):
  ```java
  private AuthResponse toResponse(AuthTokens tokens) {
      log.info("Access token: {}", tokens.getAccessToken());
      log.info("Access expires in: {}", tokens.getAccessExpiresInSeconds());
      log.info("Refresh token: {}", tokens.getRefreshToken());
  ```
- **Tại sao nguy hiểm?**: Bất kỳ ai xem được file log hoặc hệ thống thu thập log đều lấy được `refreshToken` thô và `accessToken` của mọi người dùng vừa đăng nhập. Điều này cũng vi phạm quy tắc bảo mật đã ghi trong `AGENTS.md`.
- **Cách sửa**: Xoá ngay 3 dòng `log.info` đó trong `AuthController.toResponse()`.

### 2.2. [NGHIÊM TRỌNG - Logic] Điều kiện kiểm tra IP trong `HttpUtils.getClientIp()` bị ngược (`HttpUtils.java`)
- **Vị trí**: `src/main/java/.../utils/HttpUtils.java` (dòng 25):
  ```java
  if (ip != null && !ip.isBlank() && "unknown".equalsIgnoreCase(ip)) {
      return ip.split(",")[0].trim();
  }
  ```
- **Tại sao sai?**: Bạn viết `"unknown".equalsIgnoreCase(ip)` (thiếu dấu phủ định `!`). Khi có Reverse Proxy (Nginx/Cloudflare) truyền IP thật qua `X-Forwarded-For: 113.161.x.x`, biểu thức `"unknown".equalsIgnoreCase("113.161.x.x")` trả về `false`! Hàm sẽ bỏ qua hết các header và rơi xuống `request.getRemoteAddr()` (luôn là IP của Nginx/Docker proxy).
  - **Hậu quả**: Toàn bộ người dùng trên trang web bị coi là cùng 1 địa chỉ IP → 1 người đọc chương truyện là tất cả người khác đọc trong 30 phút sau không được tính view; 1 người gọi quá 60 request/phút là **cả server bị chặn Rate Limit 429**!
- **Cách sửa**:
  ```java
  if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
      return ip.split(",")[0].trim();
  }
  ```
  *(Lưu ý nâng cao khi lên Production thật)*: Header `X-Forwarded-For` có thể bị client giả mạo nếu client gọi thẳng vào cổng 8080 hoặc nối thêm vào đầu header. Khi triển khai sau Cloudflare/Nginx, hãy bật cấu hình sẵn có của Spring Boot trong `application.yaml`:
  ```yaml
  server:
    forward-headers-strategy: native
  ```
  và chỉ cho phép kết nối từ dải IP của Nginx/Cloudflare vào cổng 8080.

### 2.3. [Bảo mật & Ổn định] Không giới hạn `size` phân trang và không kiểm tra hợp lệ `sortBy`
- **Vấn đề 1 (`size`)**: Người dùng có thể truyền `?page=0&size=999999` hoặc `?page=-1&size=0`, gây quá tải DB hoặc văng `IllegalArgumentException` (HTTP 500).
- **Vấn đề 2 (`sortBy`)**: Nếu người dùng truyền `?sortBy=hackedField`, Spring Data JPA ném `org.springframework.data.mapping.PropertyReferenceException` chưa được xử lý riêng, dẫn tới trả về lỗi `500 Internal Server Error` (`9001`) thay vì `400 Bad Request` hoặc fallback về trường mặc định.
- **Cách sửa dễ bảo trì nhất**: Tạo một class tiện ích `PaginationUtils.java` trong package `utils/` để dùng chung cho mọi Service:
  ```java
  package com.example.mymangaapp.mymangaapp.utils;

  import org.springframework.data.domain.PageRequest;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.domain.Sort;

  import java.util.Set;

  public final class PaginationUtils {
      private static final int MAX_PAGE_SIZE = 50;

      private PaginationUtils() {}

      public static Pageable createPageable(
              int page,
              int size,
              String sortBy,
              String defaultSortBy,
              Set<String> allowedSortFields,
              Sort.Direction direction
      ) {
          int safePage = Math.max(page, 0);
          int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
          String safeSort = (sortBy != null && allowedSortFields.contains(sortBy))
                  ? sortBy
                  : defaultSortBy;
          return PageRequest.of(safePage, safeSize, Sort.by(direction, safeSort));
      }
  }
  ```

### 2.4. [Lỗi Nghiệp vụ] Sinh `username` từ Email bị trùng khi khác tên miền (`SocialAccountService.java`)
- **Vị trí**: `SocialAccountService.createUser()` gọi `UsernameGenerator.fromEmail(email)` chỉ cắt phần trước dấu `@`.
- **Kịch bản lỗi**: User A dùng `minh2003@gmail.com` đăng ký trước → `username = "minh2003"`. Sau đó User B dùng `minh2003@outlook.com` (hoặc `@edu.vn`) đăng nhập → `UsernameGenerator` cũng trả về `"minh2003"` → Vi phạm `UNIQUE (username)` dưới DB và người dùng B không thể đăng nhập!
- **Cách sửa**: Trong `SocialAccountService.createUser()`, kiểm tra nếu `userRepository.existsByUsername(baseUsername)` thì nối thêm hậu tố ngẫu nhiên 4 ký tự:
  ```java
  String baseUsername = UsernameGenerator.fromEmail(email);
  String username = baseUsername;
  while (userRepository.existsByUsername(username)) {
      username = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 4);
  }
  ```

### 2.5. [Lỗi Nghiệp vụ] `TransGroup.slug` chưa dùng `SequenceCounter` dễ gây lỗi trùng `slug`
- **Vị trí**: `TransGroup.java` đang dùng `@PrePersist` / `@PreUpdate` gọi `SlugUtils.generateSlug(this.name)` không có số thứ tự, trong khi cột `slug` đặt `unique = true` và cột `name` đã bỏ `unique`!
  - Nếu 2 nhóm đặt cùng tên hoặc tên khác dấu nhưng ra cùng slug (ví dụ `"Hội Dịch Truyện"` và `"Hội Dịch Truyện!"`), DB sẽ ném lỗi trùng khoá `slug`!
  - Đáng chú ý là trong `InitApplicationConfig`, bạn **đã tạo sẵn** `SequenceCounterConstants.TRANSGROUP` nhưng chưa dùng!
- **Cách sửa**: Giống hệt cách bạn đã làm rất tốt ở `MangaService`:
  - Xoá `@PrePersist` / `@PreUpdate` trong `TransGroup.java`, thêm cột `Long sequenceNumber;` vào `TransGroup`.
  - Trong `TransGroupService.createTransGroup()` và `updateGroupById()`, lấy `sequenceCounterService.next(SequenceCounterConstants.TRANSGROUP)` và gọi `SlugUtils.generateSlug(name, sequenceNumber)`.

### 2.6. [Lỗi Concurrency] Race Condition khi duyệt đơn gia nhập nhóm (`GroupJoinRequestService.approveJoinGroup`)
- **Vị trí**: `GroupJoinRequestService.java` (nơi bạn đã ghi chú comment `Add concurrency protection to membership approval`).
- **Kịch bản lỗi**:
  1. Luật hiện tại chỉ chặn gửi trùng đơn vào *cùng 1 nhóm*, nên User X có thể gửi đơn `PENDING` vào cả Nhóm A và Nhóm B.
  2. Nếu Leader Nhóm A và Leader Nhóm B bấm `approveJoinGroup` cùng một thời điểm (2 transaction chạy song song), cả 2 đều đọc thấy `user.getTransGroup() == null` và cùng duyệt thành công!
  3. Ngoài ra, khi User X được duyệt vào Nhóm A, các đơn `PENDING` còn lại của User X ở Nhóm B, C chưa được tự động huỷ/từ chối.
- **Cách sửa chuẩn**:
  1. Thêm hàm khoá bi quan (`PESSIMISTIC_WRITE`) trên bảng `User` trong `UserRepository`:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT u FROM User u WHERE u.id = :id")
     Optional<User> findByIdForUpdate(@Param("id") String id);
     ```
  2. Trong `approveJoinGroup` (và `approveCreateGroup`), gọi `userRepository.findByIdForUpdate(userId)` đầu tiên để khoá dòng `User` đó lại cho tới khi transaction kết thúc.
  3. Sau khi gán user vào nhóm thành công, gọi câu `@Modifying` xoá hoặc chuyển toàn bộ các đơn `PENDING` khác của user đó sang `REJECTED`.

### 2.7. [Hiệu năng] `JwtAuthenticationFilter` đang truy vấn MySQL ở MỌI request có Token
- **Vị trí**: `JwtAuthenticationFilter.java` gọi `customUserDetailsService.loadUserById(userId)` ở mỗi request.
- **Vấn đề**: Bạn đã thiết kế JWT nhưng mỗi request vẫn chọc xuống MySQL JOIN 3 bảng (`user`, `user_roles`, `role_permissions`).
- **Cách sửa dễ và an toàn nhất với Redis**:
  - Vì bạn đã dùng Redis, khi Admin `banUser` hoặc khi đổi Role của User (duyệt nhóm, kick khỏi nhóm), bạn có thể lưu/xoá cache thông tin `CustomUserDetails` trong Redis (TTL 15 phút trùng với TTL của Access Token), hoặc đọc trực tiếp `userId` (`sub`) và `roles` (`scope`) từ JWT Claims đã được xác thực chữ ký HMAC-SHA512 và chỉ kiểm tra blacklist trong Redis!

### 2.8. [Vận hành] Các class Logging (`ApiLoggingFilter`, `AsyncConfig`, `MdcTaskDecorator`, `LogSanitizer`) đang bị comment đóng `/* ... */`
- Hiện tại 4 file này và file test `ApiLoggingFilterTest.java` đang bị bọc trong `/* ... */`.
- Bạn chỉ cần bỏ comment `/* ... */` ở cả 5 file đó để kích hoạt lại bộ lọc ghi log chuẩn chỉnh kèm `X-Request-ID` và `MDC`.

### 2.9. [Database Migration] Thay `ddl-auto: update` bằng Flyway khi lên Production
- Trong `application.yaml`, `spring.jpa.hibernate.ddl-auto: update` và `show-sql: true` rất tiện khi học và code ở local, nhưng trên Production:
  - `ddl-auto: update` có thể tự ý sửa bảng hoặc không xử lý được việc đổi tên cột, xoá ràng buộc, gây hỏng dữ liệu.
  - `show-sql: true` in trực tiếp ra `System.out` (đồng bộ, gây nghẽn I/O khi tải cao).
- **Cách sửa**:
  - Thêm dependency `org.flywaydb:flyway-core` và `org.flywaydb:flyway-mysql` vào `pom.xml`.
  - Xuất schema hiện tại thành file `src/main/resources/db/migration/V1__init_schema.sql`.
  - Ở profile `prod`, đặt `ddl-auto: validate` và `show-sql: false`.

---

## Phần 3: Các Chức Năng Cần Thêm Cho Giai Đoạn MVP Production & Hướng Dẫn Triển Khai

Để dự án **Ảo Truyện API** thực sự hoàn chỉnh như một sản phẩm thực tế (và gây ấn tượng mạnh với nhà tuyển dụng khi demo), bạn nên bổ sung **4 nhóm chức năng cốt lõi** dưới đây. Mọi hướng dẫn đều tuân thủ đúng kiến trúc hiện tại của bạn (`Controller -> Service -> Repository (DTO/Interface Projection) -> Entity`).

---

### Chức năng 1: Tìm Kiếm, Lọc Đa Tiêu Chí & Sắp Xếp Truyện (`GET /mangas`)

#### Tại sao bắt buộc phải có?
Một trang đọc truyện không thể chỉ có `GET /mangas` liệt kê toàn bộ truyện. Độc giả luôn cần:
- Tìm kiếm theo tên truyện / tên tác giả (`keyword`).
- Lọc theo thể loại (`categoryId` hoặc danh sách `categoryIds`).
- Lọc theo trạng thái (`status = ONGOING | COMPLETED | ONHOLD`).
- Sắp xếp theo: Mới cập nhật chương (`updatedAt`), Mới đăng (`createdAt`), Xem nhiều nhất (`totalViews`).

#### Hướng dẫn triển khai dễ bảo trì, dễ mở rộng

1. **Thêm cột `totalViews` (hoặc cập nhật `updatedAt` khi đăng chương mới) vào bảng `Manga`**:
   - Mỗi khi `ChapterService.createChapter(...)` tạo chương mới thành công, hãy cập nhật `manga.setUpdatedAt(Instant.now())` để bộ truyện đó nhảy lên đầu danh sách "Mới cập nhật"!
   - Khi `ViewFlushScheduler` cộng view vào bảng `chapter`, chạy thêm 1 câu batch cộng dồn view vào `manga.total_views` để sắp xếp "Top lượt xem" trực tiếp trên cột có đánh Index mà không cần `SUM(chapter.views)` mỗi lần query.

2. **Tạo `MangaFilterRequest.java` trong `dto/manga/request/`**:
   ```java
   @Data
   public class MangaFilterRequest {
       String keyword;
       MangaStatus status;
       Long categoryId;
       int page = 0;
       int size = 24;
       String sortBy = "updatedAt";
   }
   ```

3. **Viết câu query DTO-based Projection hỗ trợ tìm kiếm & lọc trong `MangaRepository.java`**:
   ```java
   @Query("""
           SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
               m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
               m.status, m.description, m.createdAt, m.updatedAt
           )
           FROM Manga m
           WHERE (:keyword IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                   OR LOWER(m.authorsName) LIKE LOWER(CONCAT('%', :keyword, '%')))
             AND (:status IS NULL OR m.status = :status)
             AND (:categoryId IS NULL OR EXISTS (
                 SELECT 1 FROM m.categories c WHERE c.id = :categoryId
             ))
   """)
   Page<MangaResponse> searchPublicMangas(
           @Param("keyword") String keyword,
           @Param("status") MangaStatus status,
           @Param("categoryId") Long categoryId,
           Pageable pageable
   );
   ```
   > **Mẹo phỏng vấn cực hay**: Chú ý cách lọc theo `categoryId` dùng `EXISTS (SELECT 1 FROM m.categories c WHERE c.id = :categoryId)` thay vì `JOIN m.categories c`. Nếu dùng `JOIN`, bạn phải thêm `DISTINCT` (gây tốn chi phí filesort trong MySQL); còn dùng `EXISTS` thì bảng `manga` không bao giờ bị nhân bản dòng và kết hợp hoàn hảo với `SELECT new MangaResponse(...)`!

---

### Chức năng 2: Hoàn Thiện Luồng Chương Truyện (Sửa Chương & Điều Hướng Chương Trước/Sau)

#### Tại sao cần thêm?
1. Hiện tại `ChapterController` mới chỉ có `POST` (tạo) và `DELETE` (xoá), chưa có `PATCH /mangas/{mangaId}/chapters/{chapterId}` để nhóm dịch sửa lại tiêu đề chương hoặc thay lại các trang ảnh bị lỗi chính tả.
2. Khi độc giả đang ở màn hình đọc chương (`ChapterReaderPage`), họ cần biết: tên truyện, tiêu đề chương hiện tại, danh sách ảnh, **ID của chương trước (`prevChapterId`)** và **ID của chương tiếp theo (`nextChapterId`)** để bấm nút *"Chương trước / Chương sau"*.

#### Hướng dẫn triển khai
1. **Thêm thông tin điều hướng vào `ChapterRepository`**:
   ```java
   // Tìm ID của chương liền trước (chapterIndex nhỏ hơn gần nhất)
   @Query("""
           SELECT c.id FROM Chapter c
           WHERE c.manga.id = :mangaId AND c.chapterIndex < :currentIndex
           ORDER BY c.chapterIndex DESC
           LIMIT 1
   """)
   Optional<String> findPrevChapterId(@Param("mangaId") String mangaId, @Param("currentIndex") Integer currentIndex);

   // Tìm ID của chương liền sau (chapterIndex lớn hơn gần nhất)
   @Query("""
           SELECT c.id FROM Chapter c
           WHERE c.manga.id = :mangaId AND c.chapterIndex > :currentIndex
           ORDER BY c.chapterIndex ASC
           LIMIT 1
   """)
   Optional<String> findNextChapterId(@Param("mangaId") String mangaId, @Param("currentIndex") Integer currentIndex);
   ```
2. **Cập nhật chương (`PATCH /mangas/{mangaId}/chapters/{chapterId}`)**:
   - Tạo `ChapterUpdateRequest` (gồm `Integer chapterIndex`, `String title`, `List<String> pageUrls` — các trường đều optional).
   - Nếu chỉ đổi `title` hoặc `chapterIndex`: chỉ cập nhật trên bảng `chapter` (kiểm tra trùng `chapterIndex`).
   - Nếu gửi `pageUrls` mới: promote các file đang nằm ở `tmp/`, giữ nguyên các URL cũ đã thuộc `mangas/{mangaId}/chapter/{chapterId}/`, cập nhật lại `chapter.getPages()`, và dọn các file ảnh cũ không còn nằm trong danh sách sau khi transaction commit.

---

### Chức năng 3: Theo Dõi Truyện (Bookmark/Follow) & Lịch Sử Đọc (Reading History)

#### Tại sao cần thêm?
Đây là lý do lớn nhất để một độc giả chịu bấm "Đăng nhập bằng Google" trên web đọc truyện! Họ muốn lưu bộ truyện yêu thích vào tủ sách và nhớ mình đang đọc dở tới chương nào.

#### Thiết kế Entity dễ mở rộng
1. **Bảng `Bookmark` (`entity/Bookmark.java`)**:
   - Quan hệ `@ManyToOne(fetch = LAZY)` tới `User` và `Manga`.
   - Ràng buộc duy nhất `@UniqueConstraint(columnNames = {"user_id", "manga_id"})`.
   - API:
     - `POST /mangas/{mangaId}/bookmarks`: Thêm vào tủ truyện (Idempotent: nếu đã có thì giữ nguyên).
     - `DELETE /mangas/{mangaId}/bookmarks`: Bỏ theo dõi.
     - `GET /users/me/bookmarks?page=0&size=24`: Trả về `PaginatedResponse<MangaSummaryResponse>` bằng **DTO-based Projection**:
       ```java
       @Query("""
               SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse(
                   m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                   m.status, m.description, m.createdAt
               )
               FROM Bookmark b
               JOIN b.manga m
               WHERE b.user.id = :userId
               ORDER BY b.createdAt DESC
       """)
       Page<MangaSummaryResponse> findBookmarkedMangas(@Param("userId") String userId, Pageable pageable);
       ```

2. **Bảng `ReadingHistory` (`entity/ReadingHistory.java`)**:
   - Lưu tiến độ đọc của user cho từng bộ truyện: `(id, user_id, manga_id, last_chapter_id, last_read_at)`.
   - Đặt `@UniqueConstraint(columnNames = {"user_id", "manga_id"})`: Mỗi user chỉ có **tối đa 1 dòng lịch sử cho mỗi bộ truyện** (khi đọc sang chương mới của cùng bộ truyện đó thì chỉ `UPDATE last_chapter_id` và `last_read_at` bằng `INSERT ... ON DUPLICATE KEY UPDATE`).
   - **Tại sao thiết kế 1 dòng / (user, manga) lại cực kỳ quan trọng?**: Nếu bạn lưu 1 dòng cho mỗi lần bấm đọc chương, bảng lịch sử sẽ phình lên hàng chục triệu dòng rất nhanh. Giữ đúng 1 dòng trên mỗi cặp `(user_id, manga_id)` giúp bảng luôn nhỏ gọn và truy vấn "Truyện đang đọc dở" cực nhanh!

---

### Chức năng 4: Bình Luận (Comments) Theo Truyện & Chương

#### Thiết kế chuẩn dễ bảo trì
- Tạo `entity/Comment.java` kế thừa `BaseEntity`:
  - `id` (UUID)
  - `user` (`@ManyToOne(fetch = LAZY)`)
  - `manga` (`@ManyToOne(fetch = LAZY, optional = false)`)
  - `chapter` (`@ManyToOne(fetch = LAZY)` — có thể `null` nếu bình luận ở trang giới thiệu truyện, hoặc khác `null` nếu bình luận ở trong 1 chương cụ thể)
  - `parentComment` (`@ManyToOne(fetch = LAZY)` — hỗ trợ trả lời bình luận 1 cấp)
  - `content` (`@Column(nullable = false, length = 1000)`)
- Đánh Index trên `(manga_id, created_at DESC)` và `(chapter_id, created_at DESC)`.
- Dùng **DTO-based Projection** `CommentResponse` JOIN thẳng sang `User` để lấy `username`, `displayName`, `avatar` của người bình luận và `chapterIndex` (nếu có) chỉ trong 1 câu SQL!

---

### Chức năng 5: Đóng Gói Docker Compose, Flyway & Tách Profile `dev` / `prod`

Để chạy ứng dụng mượt mà trên máy bất kỳ nhà tuyển dụng nào hoặc deploy lên VPS (Ubuntu/Render/Railway/AWS EC2):

1. **Tạo file `docker-compose.yml` ở thư mục gốc** để dựng nhanh MySQL + Redis chỉ bằng 1 lệnh `docker compose up -d`:
   ```yaml
   services:
     mysql:
       image: mysql:8.4
       container_name: aotruyen-mysql
       restart: unless-stopped
       environment:
         MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root1234}
         MYSQL_DATABASE: mymangaapp
       ports:
         - "3306:3306"
       volumes:
         - mysql_data:/var/lib/mysql
       command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

     redis:
       image: redis:7.4-alpine
       container_name: aotruyen-redis
       restart: unless-stopped
       ports:
         - "6379:6379"
       volumes:
         - redis_data:/data

   volumes:
     mysql_data:
     redis_data:
   ```

2. **Tạo profile test độc lập (`src/test/resources/application-test.yaml`)**:
   - Hiện tại test `MymangaappApplicationTests` đòi hỏi phải có đủ biến môi trường thật của MySQL, Redis, Cloudflare R2 mới khởi động được Context.
   - Hãy thêm H2 Database (scope `test`) hoặc Testcontainers và tạo `src/test/resources/application.yaml` với các giá trị giả lập (mock) để lệnh `.\mvnw.cmd test` luôn chạy xanh (PASS) trên CI/CD (GitHub Actions) mà không cần kết nối tới R2 thật.

---

## Phần 4: Điểm Nhấn Để Bạn Ghi Vào CV & Trả Lời Phỏng Vấn Thực Tập

Dự án **Ảo Truyện API** của bạn hiện đã có rất nhiều kỹ thuật vượt tầm một đồ án sinh viên thông thường. Khi viết CV hoặc đi phỏng vấn, hãy nhấn mạnh 6 điểm sau:

1. **Tối ưu truy vấn JPA/Hibernate bằng DTO-based & Interface-based Projections**:
   - Giải thích rõ tại sao không dùng `@EntityGraph` bừa bãi với `Pageable` (tránh lỗi `HHH90003004` phân trang trên RAM và Cartesian Product), mà tách thành 1 câu DTO Projection lấy trang chính + 2 câu Interface Projection nạp batch `WHERE id IN (...)`.
2. **Cơ chế xác thực 2 tầng an toàn (Access JWT + Opaque Refresh Token Rotation trên Redis)**:
   - Không lưu Refresh Token dạng thô mà băm SHA-256; dùng Lua script (`refresh_token.lua`) để xoay vòng nguyên tử (atomic), có khoảng ân hạn (`grace period`) cho các tab trình duyệt gọi song song, và tự động thu hồi toàn bộ họ token (`revokeFamily`) khi phát hiện token cũ bị đánh cắp và tái sử dụng.
3. **Đệm lượt xem (Write-Behind Caching) với Redis + JdbcTemplate Batch Update**:
   - Không `UPDATE chapter SET views = views + 1` trực tiếp xuống MySQL ở mỗi lượt đọc (tránh row lock contention). Thay vào đó chống spam view 30 phút bằng `SETNX` trên Redis, tăng bộ đếm trong Redis Hash (`HINCRBY`), và dùng `RENAMENX` + `JdbcTemplate.batchUpdate` đồng bộ xuống MySQL mỗi 5 phút theo thứ tự ID để chống Deadlock.
4. **Xử lý đồng bộ giữa Database Transaction và Cloud Object Storage (Cloudflare R2)**:
   - Sử dụng `TransactionSynchronizationManager` (`StorageCleanup`) để chỉ xoá file trên R2 **sau khi DB transaction đã commit** (`afterCommit`), hoặc tự động dọn file mới copy nếu DB transaction bị **rollback** (`afterCompletion`), kết hợp luồng upload tạm (`tmp/`) và chuẩn hoá ảnh sang `WebP` có kiểm soát kích thước chống tràn RAM (`MAX_PIXELS`).
5. **Rate Limiting phân tán với Redis + Lua Script + Spring AOP**:
   - Tự xây dựng annotation `@RateLimit` và `RateLimitAspect` chạy script Lua nguyên tử theo `IP` hoặc `USER_ID`.
6. **Xử lý Concurrency bằng Pessimistic Locking (`SELECT ... FOR UPDATE`)**:
   - Áp dụng trên bảng `SequenceCounter` khi cấp số thứ tự sinh `slug` cho Manga và khi xoá cứng Manga (`findForUpdateById`).
