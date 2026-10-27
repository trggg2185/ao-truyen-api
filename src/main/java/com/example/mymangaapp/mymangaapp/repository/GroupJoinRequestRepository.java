package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.groupjoin.response.JoinRequestResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupJoinRequest;
import com.example.mymangaapp.mymangaapp.enums.GroupJoinRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, String> {

    /*
     * DTO-based Projection thay thế cho 4 hàm @EntityGraph(attributePaths = {"transGroup", "user"}) cũ
     * (findAllByUserId, findAllByUserIdAndStatus, findAllByTransGroupId, findAllByTransGroupIdAndStatus).
     *
     * Cách hoạt động:
     * - JOIN 3 bảng (group_join_request, trans_group, user) nhưng chỉ rút ra đúng các cột tóm tắt
     *   và truyền vào constructor phẳng của JoinRequestResponse.
     * - Không khởi tạo 3 Entity cho mỗi dòng kết quả và không cần chạy stream().map(mapper) ở Service.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.groupjoin.response.JoinRequestResponse(
                r.id, r.status, r.createdAt, r.updatedAt,
                g.id, g.name, g.slug, g.avatar,
                u.id, u.username, u.displayName, u.avatar, u.status
            )
            FROM GroupJoinRequest r
            JOIN r.transGroup g
            JOIN r.user u
            WHERE (:userId IS NULL OR u.id = :userId)
              AND (:groupId IS NULL OR g.id = :groupId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
    """)
    List<JoinRequestResponse> findResponses(
            @Param("userId") String userId,
            @Param("groupId") String groupId,
            @Param("status") GroupJoinRequestStatus status);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    @NonNull
    Optional<GroupJoinRequest> findWithDetailsById(@NonNull String id);

    boolean existsByTransGroupIdAndUserIdAndStatus(String groupId, String userId, GroupJoinRequestStatus status);

    boolean existsByUserIdAndStatus(String userId, GroupJoinRequestStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE GroupJoinRequest r
            SET r.status = REJECTED
            WHERE r.status = PENDING AND r.transGroup.id = :groupId
    """)
    void rejectPendingByGroupId(@Param("groupId") String groupId);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    Optional<GroupJoinRequest> findByTransGroupIdAndUserIdAndStatus(
            String groupId,
            String userId,
            GroupJoinRequestStatus status
    );

}
