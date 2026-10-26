package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

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

   Page<Manga> findAllByStatus(MangaStatus status, Pageable pageable);

   @EntityGraph(attributePaths = { "categories", "transGroups" })
   @NonNull
   Page<Manga> findAll(@NonNull Pageable pageable);

   // Tìm tất cả manga mà nhóm này dịch
   @Query("""
            SELECT DISTINCT m FROM Manga m
            JOIN m.transGroups tg
            WHERE tg.id = :groupId
   """)
   List<Manga> findAllByGroupId(String groupId);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT m FROM Manga m WHERE m.id = :mangaId")
   Optional<Manga> findForUpdateById(@Param("mangaId") String mangaId);

   @EntityGraph(attributePaths = { "categories", "transGroups" })
   Optional<Manga> findWithDetailsById(String id);

   @EntityGraph(attributePaths = { "categories", "transGroups" })
   Optional<Manga> findWithDetailsBySlug(String slug);

   @EntityGraph(attributePaths = { "transGroups" })
   Optional<Manga> findWithTransGroupsById(String id);

   // là check xem có đúng là leader của nhóm đang dịch manga này không
   @Query("""
            SELECT COUNT(m) > 0 FROM Manga m
            JOIN m.transGroups tg
            WHERE m.id = :mangaId AND tg.leader.id = :userId AND tg.status = :status
   """)
   boolean isLeaderOfManga(String userId, String mangaId, TransGroupStatus status);

   // Group membership: tìm mọi manga mà group tham gia
   @Query("""
            SELECT DISTINCT m FROM Manga m
            JOIN m.transGroups tg
            WHERE tg.id = :groupId
    """)
   Page<Manga> findAllByGroupMembershipId(
           @Param("groupId") String groupId,
           Pageable pageable);

   // Check user này có phải là thànhviên hoặc leader của các nhómdịch truyện này không
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
           @Param("status")TransGroupStatus status);


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

   // xoá các liên kết giữa truyện và các thể loại
   @Modifying
   @Query(value = "DELETE FROM manga_categories WHERE manga_id = :mangaId", nativeQuery = true)
   void deleteCategoryLinks(@Param("mangaId") String mangaId);

   // Xoá liên kết với các group đang dịch
   @Modifying
   @Query(value = "DELETE FROM manga_transgroups WHERE manga_id = :mangaId", nativeQuery = true)
   void deleteGroupLinks(@Param("mangaId") String mangaId);

   // xoá 1 bản ghi manga
   @Modifying
   @Query(value = "DELETE FROM manga WHERE id = :mangaId", nativeQuery = true)
   void deleteRowById(@Param("mangaId") String mangaId);

}
