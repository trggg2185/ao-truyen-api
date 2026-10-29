package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaManagementResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.repository.projection.MangaCategoryProjection;
import com.example.mymangaapp.mymangaapp.repository.projection.MangaTransGroupProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MangaRepository extends JpaRepository<Manga, String> {

    // Check xem có thật là được nhóm dịch này dịch không
    @Query("""
            SELECT COUNT(m) > 0 FROM Manga m
            JOIN m.transGroups tg
            WHERE m.id = :mangaId AND tg.id = :groupId AND tg.status = :status
    """)
    boolean isMangaTranslatedByGroup(
            @Param("groupId") String groupId,
            @Param("mangaId") String mangaId,
            @Param("status") TransGroupStatus status);

    /*
     * =========================================================================================
     * TỐI ƯU TRUY VẤN MANGA BẰNG KẾT HỢP DTO-BASED PROJECTION & INTERFACE-BASED PROJECTION
     * =========================================================================================
     * Vấn đề của code cũ:
     *   Code cũ dùng `@EntityGraph(attributePaths = {"categories", "transGroups"}) Page<Manga> findAll(Pageable)`
     *   gây ra 2 lỗi hiệu năng nghiêm trọng trong Hibernate:
     *   1. Lỗi HHH90003004 (firstResult/maxResults specified with collection fetch; applying in memory):
     *      Khi JOIN bảng 1-N / N-N, 1 bộ truyện có 5 thể loại sẽ biến thành 5 dòng trong ResultSet,
     *      làm SQL LIMIT/OFFSET bị sai. Để sửa, Hibernate âm thầm BỎ LIMIT/OFFSET dưới DB, kéo TOÀN BỘ
     *      bảng manga + categories + transGroups lên RAM của JVM rồi mới cắt trang trong bộ nhớ!
     *   2. Tích Đề-các (Cartesian Product): JOIN cùng lúc 2 bảng N-N (categories và transGroups)
     *      khiến số dòng nhân lên gấp bội (ví dụ 5 thể loại x 3 nhóm dịch = 15 dòng cho 1 bộ truyện).
     *
     * Giải pháp chuẩn Production:
     *   - Bước 1: Dùng DTO-based Projection (`SELECT new ...MangaResponse(...)`) chỉ lấy các cột đơn
     *     của bảng `manga`. Lúc này SQL `LIMIT/OFFSET` chạy chuẩn 100% dưới MySQL!
     *   - Bước 2: Dùng 2 câu query Interface-based Projection (`findCategoryProjectionsByMangaIds` và
     *     `findTransGroupProjectionsByMangaIds`) truyền `WHERE m.id IN :mangaIds` để nạp toàn bộ
     *     categories và transGroups của trang hiện tại chỉ bằng đúng 2 câu SQL gọn nhẹ.
     *   => Tổng cộng cố định 3 câu SQL phẳng, cực nhanh, không bao giờ bị N+1 hay tràn RAM.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt, m.totalViews, m.latestChapterIndex
            )
            FROM Manga m
    """)
    Page<MangaResponse> findPublicPage(Pageable pageable);

    @Query("""
            SELECT  new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt, m.totalViews, m.latestChapterIndex
            )
            FROM Manga m
            WHERE m.latestChapterAt IS NOT NULL
            ORDER BY m.latestChapterAt DESC
    """)
    // Lấy danh sách 20 truyện mới cập nhật các chap mới, sắp xếp giảm dần tg latest chap của mỗi truyện
    List<MangaResponse> findRecentlyUpdatedMangas(Pageable pageable);

    @Query("""
            SELECT  new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt, m.totalViews, m.latestChapterIndex
            )
            FROM Manga m
            WHERE EXISTS (SELECT 1 FROM Chapter c WHERE c.manga.id = m.id)
            ORDER BY m.createdAt DESC
    """)
    // Lấy ds 10 truyện mới được tạo và phải có chương rồi mới tính, ko có chương nào ko tính
    List<MangaResponse> findNewlyMangas(Pageable pageable);

    // DTO-based Projection cho chi tiết 1 bộ truyện theo ID (GET /mangas/{idOrSlug})
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt, m.totalViews, m.latestChapterIndex
            )
            FROM Manga m
            WHERE m.id = :id
    """)
    Optional<MangaResponse> findProjectedById(@Param("id") String id);

    // DTO-based Projection cho chi tiết 1 bộ truyện theo Slug (GET /mangas/{idOrSlug})
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt, m.totalViews, m.latestChapterIndex
            )
            FROM Manga m
            WHERE m.slug = :slug
    """)
    Optional<MangaResponse> findProjectedBySlug(@Param("slug") String slug);

    // DTO-based Projection cho màn hình quản lý truyện của Leader (GET /mangas/{mangaId}/management)
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaManagementResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.updatedAt
            )
            FROM Manga m
            WHERE m.id = :id
    """)
    Optional<MangaManagementResponse> findManagementById(@Param("id") String id);

    /*
     * Interface-based Projection: nạp batch toàn bộ Category của danh sách mangaIds.
     * Chú ý các alias (AS mangaId, AS categoryId, AS name, AS description) phải trùng khớp
     * với tên các hàm getter trong MangaCategoryProjection.
     */
    @Query("""
            SELECT m.id AS mangaId,
                   c.id AS categoryId,
                   c.name AS name,
                   c.description AS description
            FROM Manga m
            JOIN m.categories c
            WHERE m.id IN :mangaIds
    """)
    List<MangaCategoryProjection> findCategoryProjectionsByMangaIds(@Param("mangaIds") Collection<String> mangaIds);

    /*
     * Interface-based Projection: nạp batch toàn bộ TransGroup của danh sách mangaIds.
     * Alias khớp với các hàm getter trong MangaTransGroupProjection.
     */
    @Query("""
            SELECT m.id AS mangaId,
                   g.id AS groupId,
                   g.name AS name,
                   g.slug AS slug,
                   g.avatar AS avatar,
                   g.status AS status
            FROM Manga m
            JOIN m.transGroups g
            WHERE m.id IN :mangaIds
    """)
    List<MangaTransGroupProjection> findTransGroupProjectionsByMangaIds(@Param("mangaIds") Collection<String> mangaIds);

    /*
     * DTO-based Projection cho danh sách truyện trong trang Admin (GET /admin/mangas).
     * Hỗ trợ lọc theo status (nếu status == null thì lấy tất cả).
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt
            )
            FROM Manga m
            WHERE (:status IS NULL OR m.status = :status)
    """)
    Page<MangaSummaryResponse> findSummaries(@Param("status") MangaStatus status, Pageable pageable);

    /*
     * DTO-based Projection cho danh sách truyện mà 1 nhóm dịch tham gia (GET /transgroups/{groupId}/mangas).
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse(
                m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
                m.status, m.description, m.createdAt
            )
            FROM Manga m
            JOIN m.transGroups tg
            WHERE tg.id = :groupId
    """)
    Page<MangaSummaryResponse> findSummariesByGroupId(@Param("groupId") String groupId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Manga m WHERE m.id = :mangaId")
    Optional<Manga> findForUpdateById(@Param("mangaId") String mangaId);

    // Vẫn giữ cho luồng updateMangaById (cần Entity có quản lý trạng thái để sửa ManyToMany)
    @EntityGraph(attributePaths = { "categories", "transGroups" })
    Optional<Manga> findWithDetailsById(String id);

    @EntityGraph(attributePaths = { "transGroups" })
    Optional<Manga> findWithTransGroupsById(String id);

    // Check xem có đúng là leader của nhóm đang dịch manga này không
    @Query("""
            SELECT COUNT(m) > 0 FROM Manga m
            JOIN m.transGroups tg
            WHERE m.id = :mangaId AND tg.leader.id = :userId AND tg.status = :status
    """)
    boolean isLeaderOfManga(String userId, String mangaId, TransGroupStatus status);

    // Check user này có phải là thành viên hoặc leader của các nhóm dịch truyện này không
    // đồng thời các nhóm dịch phải là APPROVED
    @Query("""
            SELECT COUNT(g) > 0
            FROM Manga m
            JOIN m.transGroups g
            WHERE m.id = :mangaId
                AND g.status = :status
                AND (
                    g.leader.id = :userId
                    OR EXISTS (
                        SELECT 1 FROM User u
                        WHERE u.id = :userId
                            AND u.transGroup = g
                    )
                )
    """)
    boolean isMemberOrLeaderOfAnyGroupOfManga(
            @Param("userId") String userId,
            @Param("mangaId") String mangaId,
            @Param("status") TransGroupStatus status);

    /**
     * Tìm những manga nào mà group :groupId là group ACTIVE DUY NHẤT (hoặc cuối cùng) đang dịch
     * <=> tìm các manga mà nếu group này rời đi (ngừng dịch) thì manga đó sẽ không còn group ACTIVE nào khác đang dịch nữa.
     */
    @Query("""
            SELECT m.id FROM Manga m JOIN m.transGroups g
            WHERE g.id = :groupId
               AND (SELECT COUNT(g2) FROM Manga m2 JOIN m2.transGroups g2
                    WHERE m2.id = m.id AND g2.status = :status) <= 1
    """)
    List<String> findMangaIdsWhereGroupIsLastActive(
            @Param("groupId") String groupId,
            @Param("status") TransGroupStatus status);

    // Xoá các pages bằng manga id
    @Modifying
    @Query(value = "DELETE p FROM page p JOIN chapter c ON p.chapter_id = c.id WHERE c.manga_id = :mangaId", nativeQuery = true)
    void deletePagesById(@Param("mangaId") String mangaId);

    // Xoá tất cả chapters bằng manga id
    @Modifying
    @Query(value = "DELETE FROM chapter WHERE manga_id = :mangaId", nativeQuery = true)
    void deleteChaptersById(@Param("mangaId") String mangaId);

    // Xoá các liên kết giữa truyện và các thể loại
    @Modifying
    @Query(value = "DELETE FROM manga_categories WHERE manga_id = :mangaId", nativeQuery = true)
    void deleteCategoryLinks(@Param("mangaId") String mangaId);

    // Xoá liên kết với các group đang dịch
    @Modifying
    @Query(value = "DELETE FROM manga_transgroups WHERE manga_id = :mangaId", nativeQuery = true)
    void deleteGroupLinks(@Param("mangaId") String mangaId);

    // Xoá 1 bản ghi manga
    @Modifying
    @Query(value = "DELETE FROM manga WHERE id = :mangaId", nativeQuery = true)
    void deleteRowById(@Param("mangaId") String mangaId);

}
