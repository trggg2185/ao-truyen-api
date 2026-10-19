package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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

   // Admin list: join thêm ownerTransGroup / transGroups / categories để view dashboard chi tiết hơn
   @Query("SELECT m FROM Manga m")
   Page<Manga> findAllDetailed(Pageable pageable);

   @Query("SELECT m FROM Manga m WHERE m.status = :status")
   Page<Manga> findAllDetailedByStatus(@Param("status") MangaStatus status, Pageable pageable);

   @EntityGraph(attributePaths = { "categories", "transGroups" })
   Optional<Manga> findWithDetailsById(String id);

   // Dùng khi rút nhóm: khóa dòng manga, hai request cùng rút sẽ xếp hàng
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT m FROM Manga m WHERE m.id = :id")
   Optional<Manga> findByIdForUpdate(@Param("id") String id);

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

}
