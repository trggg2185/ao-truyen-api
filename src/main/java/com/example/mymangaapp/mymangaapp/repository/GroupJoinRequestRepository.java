package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.GroupJoinRequest;
import com.example.mymangaapp.mymangaapp.enums.GroupJoinRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, String> {

    @EntityGraph(attributePaths = { "transGroup", "user" })
    @NonNull
    List<GroupJoinRequest> findAll();

    // Lấy ds các yc của 1 nhóm dịch theo trạng thái yc đó
    @EntityGraph(attributePaths = { "transGroup", "user" })
    List<GroupJoinRequest> findAllByTransGroupIdAndStatus(String groupId, GroupJoinRequestStatus status);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    List<GroupJoinRequest> findAllByTransGroupId(String groupId);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    @NonNull
    Optional<GroupJoinRequest> findWithDetailsById(@NonNull String id);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    List<GroupJoinRequest> findAllByUserId(String userId);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    List<GroupJoinRequest> findAllByUserIdAndStatus(String userId, GroupJoinRequestStatus status);

    boolean existsByTransGroupIdAndUserIdAndStatus(String groupId, String userId, GroupJoinRequestStatus status);

    @EntityGraph(attributePaths = { "transGroup", "user" })
    Optional<GroupJoinRequest> findByTransGroupIdAndUserIdAndStatus(
            String groupId,
            String userId,
            GroupJoinRequestStatus status
    );

}
