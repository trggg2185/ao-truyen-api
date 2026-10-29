package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.constant.SequenceCounterConstants;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupOfUserResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.*;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import com.example.mymangaapp.mymangaapp.enums.GroupJoinRequestStatus;
import com.example.mymangaapp.mymangaapp.enums.MyGroupStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.*;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.PaginationUtils;
import com.example.mymangaapp.mymangaapp.utils.SlugUtils;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import com.example.mymangaapp.mymangaapp.utils.UUIDUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service @Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class TransGroupService {

    UserRepository userRepository;
    TransGroupRepository transGroupRepository;
    RoleRepository roleRepository;
    GroupCreationRequestRepository groupCreationRequestRepository;
    GroupJoinRequestRepository groupJoinRequestRepository;
    MangaRepository mangaRepository;

    TransGroupMapper transGroupMapper;

    ObjectStorage objectStorage;
    StorageService storageService;
    SequenceCounterService sequenceCounterService;

    @Transactional
    public TransGroup createTransGroup(GroupCreationRequest groupCreationRequest) {
        User creator = groupCreationRequest.getCreator();

        Role translatorRole = roleRepository
                .findById(RoleConstants.TRANSLATOR)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));

        if (!userRepository.hasRole(creator.getId(), RoleConstants.ADMIN)) {
            creator.getRoles().add(translatorRole);
        }
        creator = userRepository.save(creator);

        TransGroup transGroup = TransGroup.builder()
                .name(groupCreationRequest.getGroupName())
                .description(groupCreationRequest.getDescription())
                .leader(creator)
                .slug(SlugUtils.generateSlug(groupCreationRequest.getGroupName()))
                .sequenceNumber(sequenceCounterService.next(SequenceCounterConstants.TRANSGROUP))
                .build();

        return transGroupRepository.save(transGroup);
    }

    // ------------------------------------chức năng cho user đã đăng nhập ----------------------------------//

    // Lấy nhóm dịch mà user đang tham gia bằng DTO-based Projection (không load Entity User hay TransGroup)
    public TransGroupOfUserResponse getMyGroup() {
        String currentUserId = SecurityUtils.getCurrentUserId();

        // 1. Check user đang là MEMBER của nhóm APPROVED nào không (chỉ SELECT 4 cột id, name, slug, avatar)
        Optional<TransGroupSummaryResponse> memberGroupOpt = transGroupRepository
                .findMemberGroupSummary(currentUserId, TransGroupStatus.APPROVED);
        if (memberGroupOpt.isPresent()) {
            return TransGroupOfUserResponse.builder()
                    .transGroup(memberGroupOpt.get())
                    .status(MyGroupStatus.MEMBER)
                    .build();
        }

        // 2. Check user có đang làm LEADER của nhóm APPROVED nào không
        Optional<TransGroupSummaryResponse> leaderGroupOpt = transGroupRepository
                .findSummaryByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED);
        if (leaderGroupOpt.isPresent()) {
            return TransGroupOfUserResponse.builder()
                    .transGroup(leaderGroupOpt.get())
                    .status(MyGroupStatus.LEADER)
                    .build();
        }

        // 3. Check user có đang gửi yêu cầu tạo nhóm hoặc tham gia nhóm chờ duyệt không
        if (groupCreationRequestRepository.existsByCreatorIdAndStatus(currentUserId, GroupCreationRequestStatus.PENDING) ||
            groupJoinRequestRepository.existsByUserIdAndStatus(currentUserId, GroupJoinRequestStatus.PENDING)
        ) {
            return TransGroupOfUserResponse.builder()
                    .status(MyGroupStatus.PENDING_REQUEST)
                    .build();
        }

        return TransGroupOfUserResponse.builder()
                .status(MyGroupStatus.NONE)
                .build();
    }

    // ----------------------------------- chức năng public (cho khách) -----------------------------------//

    // Lấy tất cả nhóm dịch đã được chấp thuận bằng DTO-based Projection
    public PaginatedResponse<TransGroupSummaryResponse> getGroups(
            int page, int size,
            @NonNull String sortBy
    ) {

        // sort giảm dần
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, Sort.Direction.DESC);

        Page<TransGroupSummaryResponse> dtoPage = transGroupRepository
                .findSummaries(TransGroupStatus.APPROVED, pageable);

        return PaginatedResponse.of(dtoPage);
    }

    /*
     * Xem chi tiết trang cá nhân của 1 nhóm dịch (GET /transgroups/{idOrSlug}).
     * - Đã xoá TransGroupDetailsResponse dư thừa và thay bằng TransGroupResponse (có đầy đủ cả avatar).
     * - Không còn query toàn bộ translatedMangas không phân trang ở đây nữa (tránh tràn bộ nhớ khi nhóm
     *   dịch hàng trăm bộ truyện); frontend lấy danh sách truyện của nhóm qua endpoint phân trang riêng:
     *   GET /transgroups/{groupId}/mangas.
     */
    public TransGroupResponse getGroupByIdOrSlug(@NonNull String idOrSlug) {
        TransGroup transGroup = UUIDUtils.isUuid(idOrSlug)
                ? transGroupRepository
                        .findWithDetailsById(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND))
                : transGroupRepository
                        .findWithDetailsBySlug(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        return transGroupMapper.toTransGroupResponse(transGroup);
    }

    // -------------------------------- chức năng của admin -------------------------------- //

    // Lấy danh sách nhóm dịch cho admin bằng DTO-based Projection (hỗ trợ lọc status)
    public PaginatedResponse<TransGroupSummaryResponse> getGroups(
            TransGroupStatus status, int page,
            int size, @NonNull String sortBy
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, Sort.Direction.DESC);

        Page<TransGroupSummaryResponse> dtoPage = transGroupRepository
                .findSummaries(status, pageable);

        return PaginatedResponse.of(dtoPage);
    }

    // ------------------------ chức năng dành cho leader nhóm dịch hoặc admin ------------------------ //

    @Transactional
    public void softDeleteGroupById(@NonNull String id) {
        TransGroup transGroup = transGroupRepository
                .findWithLeaderAndRolesById(id)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        if (transGroup.getStatus().equals(TransGroupStatus.DELETED)) {
            throw new AppException(ResponseCode.TRANSGROUP_ALREADY_DELETED);
        }

        boolean isAdmin = SecurityUtils.isAdmin();
        boolean isLeader = SecurityUtils.getCurrentUserId().equals(transGroup.getLeader().getId());

        if (!isAdmin && !isLeader) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        transGroup.setStatus(TransGroupStatus.DELETED);

        List<String> orphanMangaIds = mangaRepository
                .findMangaIdsWhereGroupIsLastActive(id, TransGroupStatus.APPROVED);

        if (!orphanMangaIds.isEmpty()) {
            if (isLeader) throw new AppException(ResponseCode.TRANSGROUP_IS_THE_LAST_GROUP_OF_MANGA);

            log.warn("Admin xoá nhóm {} làm {} truyện không còn nhóm nào dịch: {}",
                    id,
                    orphanMangaIds.size(),
                    orphanMangaIds);
        }

        userRepository.removeTranslatorRoleFromMembers(id);
        userRepository.clearTransGroupFromMembers(id);
        groupJoinRequestRepository.rejectPendingByGroupId(id);

        transGroup.getLeader()
                .getRoles().removeIf(role -> RoleConstants.TRANSLATOR.equals(role.getName()));
    }

    @PreAuthorize("@groupSec.isGroupLeaderOrAdmin(#id)")
    @Transactional
    public TransGroupResponse updateGroupById(@NonNull String id, @NonNull TransGroupUpdateRequest request) {
        TransGroup transGroup = transGroupRepository
                .findWithLeaderAndMembersByIdAndStatus(id, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        if (!transGroup.getStatus().equals(TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.TRANSGROUP_ALREADY_DELETED);
        }

        transGroupMapper.updateTransGroupFromRequest(transGroup, request);

        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {
            if (transGroup.getAvatar() != null && !transGroup.getAvatar().isEmpty()) {
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupAvatarPrefix(transGroup.getId()), Instant.MAX);
            }
            String groupAvatarUrl = storageService.uploadGroupAvatar(transGroup.getId(), request.getAvatarFile());
            transGroup.setAvatar(groupAvatarUrl);
        }

        if (request.getCoverFile() != null && !request.getCoverFile().isEmpty()) {
            if (transGroup.getCoverImageUrl() != null && !transGroup.getCoverImageUrl().isEmpty()) {
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupCoverPrefix(transGroup.getId()), Instant.MAX);
            }
            String groupCoverUrl = storageService.uploadGroupCover(transGroup.getId(), request.getCoverFile());
            transGroup.setCoverImageUrl(groupCoverUrl);
        }

        return transGroupMapper.toTransGroupResponse(transGroupRepository.save(transGroup));
    }

}
