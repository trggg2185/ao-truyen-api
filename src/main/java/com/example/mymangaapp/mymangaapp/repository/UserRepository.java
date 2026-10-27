package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u.id) > 0 FROM User u WHERE u.id = :userId AND u.status = BANNED")
    boolean isUserBannedById(String userId);

    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            JOIN u.roles r
            WHERE u.id = :userId AND r.name = :roleName
    """)
    boolean hasRole(String userId, String roleName);

    Optional<User> findByUsername(String username);

    /*
     * DTO-based Projection cho GET /users/me:
     * Chỉ lấy các cột cần thiết của tài khoản hiện tại đóng gói thẳng vào CurrentUserResponse,
     * không quản lý Entity User trong Persistence Context vì đây là thao tác chỉ đọc (read-only).
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse(
                u.id, u.username, u.displayName, u.email, u.facebook,
                u.discord, u.status, u.bio, u.avatar, u.createdAt
            )
            FROM User u
            WHERE u.username = :username
    """)
    Optional<CurrentUserResponse> findCurrentUserByUsername(@Param("username") String username);

    /*
     * DTO-based Projection cho GET /users/{idOrUsername} (trang cá nhân công khai):
     * Dùng LEFT JOIN sang transGroup để lấy luôn thông tin nhóm dịch (nếu user là member)
     * ngay trong cùng 1 câu SQL, tuyệt đối không SELECT cột nhạy cảm (email).
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse(
                u.id, u.username, u.displayName, u.avatar, u.bio,
                u.facebook, u.discord, u.status, u.createdAt,
                g.id, g.name, g.slug, g.avatar, g.status
            )
            FROM User u
            LEFT JOIN u.transGroup g
            WHERE u.id = :id
    """)
    Optional<PublicUserProfileResponse> findPublicProfileById(@Param("id") String id);

    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse(
                u.id, u.username, u.displayName, u.avatar, u.bio,
                u.facebook, u.discord, u.status, u.createdAt,
                g.id, g.name, g.slug, g.avatar, g.status
            )
            FROM User u
            LEFT JOIN u.transGroup g
            WHERE u.username = :username
    """)
    Optional<PublicUserProfileResponse> findPublicProfileByUsername(@Param("username") String username);

    /*
     * DTO-based Projection cho GET /admin/users (danh sách user có phân trang):
     * Chỉ SELECT đúng 5 cột (id, username, displayName, avatar, status) thay vì SELECT * toàn bảng user.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse(
                u.id, u.username, u.displayName, u.avatar, u.status
            )
            FROM User u
    """)
    Page<UserSummaryResponse> findAllSummaries(Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    Optional<User> findWithRolesById(String id);

    @EntityGraph(attributePaths = { "transGroup" })
    Optional<User> findWithGroupById(String id);

    @EntityGraph(attributePaths = { "roles", "transGroup" })
    Optional<User> findWithDetailsById(@NonNull String id);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findForAuthByUsername(@NonNull String username);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findForAuthById(@NonNull String id);

    // Thao tác sẽ duyệt tất cả các user thuộc về nhóm dịch để set trường transgroup_id về null
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.transGroup = null WHERE u.transGroup.id = :transGroupId")
    void clearTransGroupFromMembers(@Param("transGroupId") String id);

    // Native SQL để xoá role translator ra khỏi các members
    @Modifying
    @Query(value = """
            DELETE FROM user_roles
            WHERE roles_name = 'TRANSLATOR'
            AND users_id IN (SELECT id FROM user WHERE transgroup_id = :groupId)
    """, nativeQuery = true)
    void removeTranslatorRoleFromMembers(@Param("groupId") String groupId);

}
