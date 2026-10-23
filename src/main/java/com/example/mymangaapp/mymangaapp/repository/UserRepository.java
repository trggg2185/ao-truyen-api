package com.example.mymangaapp.mymangaapp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.mymangaapp.mymangaapp.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            JOIN u.roles r
            WHERE u.id = :userId AND r.name = :roleName
    """)
    boolean hasRole(String userId, String roleName);

    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = { "roles" })
    Optional<User> findWithRolesById(String id);

    @EntityGraph(attributePaths = { "transGroup" })
    Optional<User> findWithGroupById(String id);

    @EntityGraph(attributePaths = { "transGroup" })
    Optional<User> findWithGroupByUsername(String username);

    // Khi lấy user, lấy luôn roles, và khi lấy các roles, lấy luôn các permissions
    @EntityGraph(attributePaths = { "roles", "transGroup" })
    Optional<User> findWithDetailsByUsername(@NonNull String username);

    @EntityGraph(attributePaths = { "roles", "transGroup" })
    Optional<User> findWithDetailsById(@NonNull String id);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findForAuthByUsername(@NonNull String username);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findForAuthById(@NonNull String id);

    @NonNull
    Page<User> findAll(@NonNull Pageable pageable);

    // Thao tác sẽ duyệt tất cả các user thuộc về nhóm dịch để set trường transgroup_id về null
    @Modifying(clearAutomatically = true, flushAutomatically = true) // Cho truy vấn update/delete làm thay đổi dữ liệu trong db
    @Query("UPDATE User u SET u.transGroup = null WHERE u.transGroup.id = :transGroupId")
    void clearTransGroupFromMembers(@Param("transGroupId") String id);

    // Native sql để xoá role translator ra khỏi các members
    @Modifying
    @Query(value = """
            DELETE FROM user_roles
            WHERE roles_name = 'TRANSLATOR'
            AND users_id IN (SELECT id FROM user WHERE transgroup_id = :groupId)
    """, nativeQuery = true)
    void removeTranslatorRoleFromMembers(@Param("groupId") String groupId);
}
