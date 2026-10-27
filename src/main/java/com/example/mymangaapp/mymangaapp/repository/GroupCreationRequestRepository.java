package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.groupcreation.response.CreationRequestResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupCreationRequest;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupCreationRequestRepository extends JpaRepository<GroupCreationRequest, String> {

    boolean existsByCreatorIdAndStatus(String creatorId, GroupCreationRequestStatus status);

    boolean existsByGroupName(String groupName);

    @EntityGraph(attributePaths = { "creator" })
    Optional<GroupCreationRequest> findWithCreatorById(String id);

    /*
     * DTO-based Projection thay thế cho 4 hàm @EntityGraph cũ
     * (findAll, findAllByStatus, findAllByCreatorId, findAllByCreatorIdAndStatus).
     *
     * Lý do tối ưu:
     * - Trước đây dùng @EntityGraph(attributePaths = {"creator"}) sẽ kéo toàn bộ các cột của bảng
     *   group_creation_request VÀ toàn bộ các cột của bảng user (email, bio, facebook, discord, avatar...)
     *   chỉ để lấy đúng 1 trường duy nhất là `creator.username`.
     * - Với DTO Projection `SELECT new ...CreationRequestResponse(...)`, câu SQL chỉ SELECT đúng 6 cột
     *   cần thiết. Đồng thời kỹ thuật `(:param IS NULL OR ...)` giúp gộp 4 hàm truy vấn thành 1 hàm duy nhất.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.groupcreation.response.CreationRequestResponse(
                r.id, r.groupName, c.username, r.status, r.description, r.updatedAt
            )
            FROM GroupCreationRequest r
            JOIN r.creator c
            WHERE (:creatorId IS NULL OR c.id = :creatorId)
              AND (:status IS NULL OR r.status = :status)
    """)
    Page<CreationRequestResponse> findResponses(
            @Param("creatorId") String creatorId,
            @Param("status") GroupCreationRequestStatus status,
            Pageable pageable);

}
