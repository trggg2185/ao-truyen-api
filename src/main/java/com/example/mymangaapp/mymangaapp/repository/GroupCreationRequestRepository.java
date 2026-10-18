package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.GroupCreationRequest;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface GroupCreationRequestRepository extends JpaRepository<GroupCreationRequest, String> {

    boolean existsByCreatorIdAndStatus(String creatorId, GroupCreationRequestStatus status);

    boolean existsByGroupName(String groupName);

    @EntityGraph(attributePaths = { "creator" })
    Optional<GroupCreationRequest> findWithCreatorById(String id);

    @EntityGraph(attributePaths = { "creator" })
    Page<GroupCreationRequest> findAllByStatus(GroupCreationRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "creator" })
    Page<GroupCreationRequest> findAllByCreatorIdAndStatus(
            String creatorId,
            GroupCreationRequestStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = { "creator" })
    Page<GroupCreationRequest> findAllByCreatorId(String creatorId, Pageable pageable);

    @EntityGraph(attributePaths = { "creator" })
    @NonNull
    Page<GroupCreationRequest> findAll(@NonNull Pageable pageable);

}
