package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransGroupRepository extends JpaRepository<TransGroup, String> {

    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithDetailsById(String id);

    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithDetailsBySlug(String slug);

    @EntityGraph(attributePaths = { "leader", "leader.roles" })
    Optional<TransGroup> findWithLeaderAndRolesById(String id);

    @EntityGraph(attributePaths = { "members" })
    Optional<TransGroup> findWithMembersById(String id);

    // Hàm dùng cho luồng cập nhật / xin gia nhập nhóm dịch: fetch sẵn leader và members để tránh N+1 query
    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithLeaderAndMembersByIdAndStatus(String id, TransGroupStatus status);

    Optional<TransGroup> findByLeaderIdAndStatus(String leaderId, TransGroupStatus status);

    List<TransGroup> findAllByIdInAndStatus(Collection<String> id, TransGroupStatus status);

    /*
     * DTO-based Projection cho danh sách nhóm dịch có phân trang (dùng cho cả GET /transgroups
     * và GET /admin/transgroups).
     * Chỉ SELECT đúng 4 cột (id, name, slug, avatar) để tạo TransGroupSummaryResponse,
     * bỏ qua description, coverImageUrl, createdAt, updatedAt, leader_id.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse(
                g.id, g.name, g.slug, g.avatar
            )
            FROM TransGroup g
            WHERE (:status IS NULL OR g.status = :status)
    """)
    Page<TransGroupSummaryResponse> findSummaries(
            @Param("status") TransGroupStatus status,
            Pageable pageable);

    /*
     * DTO-based Projection: lấy thông tin tóm tắt của nhóm dịch mà user đang tham gia với tư cách MEMBER.
     * Dùng trong getMyGroup() để tránh phải load cả Entity User lẫn Entity TransGroup.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse(
                g.id, g.name, g.slug, g.avatar
            )
            FROM User u
            JOIN u.transGroup g
            WHERE u.id = :userId AND g.status = :status
    """)
    Optional<TransGroupSummaryResponse> findMemberGroupSummary(
            @Param("userId") String userId,
            @Param("status") TransGroupStatus status);

    /*
     * DTO-based Projection: lấy thông tin tóm tắt của nhóm dịch mà user đang làm LEADER.
     * Dùng trong getMyGroup() và getUserProfile().
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse(
                g.id, g.name, g.slug, g.avatar
            )
            FROM TransGroup g
            WHERE g.leader.id = :leaderId AND g.status = :status
    """)
    Optional<TransGroupSummaryResponse> findSummaryByLeaderIdAndStatus(
            @Param("leaderId") String leaderId,
            @Param("status") TransGroupStatus status);

    boolean existsByIdAndLeaderId(String id, String leaderId);

    boolean existsByLeaderId(String leaderId);

    boolean existsByLeaderIdAndStatus(String leaderId, TransGroupStatus status);

    @Query("""
            SELECT COUNT(g) > 0 FROM TransGroup g
            JOIN g.members m
            WHERE g.id = :groupId and m.id = :memberId
            """)
    boolean isMemberOfGroup(String groupId, String memberId);

}
