package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransGroupRepository extends JpaRepository<TransGroup, String> {


    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithDetailsById(String id);

    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithDetailsBySlug(String slug);

    @EntityGraph(attributePaths = { "leader", "leader.roles" })
    Optional<TransGroup> findWithLeaderAndRoleById(String id);

    @EntityGraph(attributePaths = { "members" })
    Optional<TransGroup> findWithMembersById(String id);

    @NonNull
    Page<TransGroup> findAll(@NonNull Pageable pageable);

    // Hàm này để tìm group theo id đã đc fetch sẵn các dữ liệu như leader, roles, permissions và members
    // vì chúng là lazy và để tránh n + 1 query
    @EntityGraph(attributePaths = { "leader", "members" })
    Optional<TransGroup> findWithLeaderAndMembersByIdAndStatus(String id, TransGroupStatus status);

    Optional<TransGroup> findByLeaderIdAndStatus(String leaderId, TransGroupStatus status);

    List<TransGroup> findAllByIdInAndStatus(Collection<String> id, TransGroupStatus status);

    Page<TransGroup> findAllByStatus(TransGroupStatus status, Pageable pageable);

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
