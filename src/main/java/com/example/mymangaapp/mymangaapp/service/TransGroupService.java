package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupDetailsResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupCreationRequest;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TransGroupService {

    UserRepository userRepository;
    TransGroupRepository transGroupRepository;
    RoleRepository roleRepository;

    TransGroupMapper transGroupMapper;

    ObjectStorage objectStorage;
    StorageService storageService;

    // method này đc gọi từ method đã có transactional thì method này ko cần transactional
    // vì method này khác tự tham gia vào transaction đó, khi fail thì rollback toàn bộ 2 method
    // hàm giúp tạo nhóm từ yêu cầu tạo nhóm
    @Transactional // vẫn nên thêm transactional để khi có method nơi khác gọi
    public TransGroup createTransGroup(GroupCreationRequest groupCreationRequest) {

        User creator = groupCreationRequest.getCreator();

        Role translatorRole = roleRepository
                .findById(RoleConstants.TRANSLATOR)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));

        // Gán role translator cho leader
        creator.getRoles().add(translatorRole);

        creator = userRepository.save(creator);

        // quyết định là trong members sẽ ko chứa leader
        TransGroup transGroup = TransGroup.builder()
                .name(groupCreationRequest.getGroupName())
                .description(groupCreationRequest.getDescription())
                .leader(creator)
                .build();

        return transGroupRepository.save(transGroup);
    }


    // ----------------------------------- chức năng public (cho khách) -----------------------------------//

    // Lấy tất cả nhóm dịch đã được chấp thuận, public
    public PaginatedResponse<TransGroupSummaryResponse> getGroups(
            int page, int size,
            @NonNull String sortBy
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<TransGroupSummaryResponse> dtoPage = transGroupRepository
                .findAllByStatus(TransGroupStatus.APPROVED, pageable)
                .map(transGroupMapper::toTransGroupSummaryResponse);

        return PaginatedResponse.of(dtoPage);
    }

    // chức năng khi 1 user vào trang cá nhân của 1 nhóm
    // hiện chi tiết toàn bộ về nhóm đó
    public TransGroupDetailsResponse getGroupById(@NonNull String id) {

        TransGroup transGroup = transGroupRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        return transGroupMapper.toTransGroupDetailsResponse(transGroup);
    }


    // -------------------------------- chức năng của admin -------------------------------- //

    // Đây cũng lấy nhóm nhưng chỉ dành cho admin
    public PaginatedResponse<TransGroupSummaryResponse> getGroups(
            TransGroupStatus status, int page,
            int size, @NonNull String sortBy
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<TransGroupSummaryResponse> dtoPage;

        // Nếu cho query string status thì lấy nhóm dịch theo status
        if (status != null) {
            dtoPage = transGroupRepository
                    .findAllByStatus(status, pageable)
                    .map(transGroupMapper::toTransGroupSummaryResponse);
        } else { // Nếu ko status thì mặc định lấy tất cả các nhóm
            dtoPage = transGroupRepository
                    .findAll(pageable)
                    .map(transGroupMapper::toTransGroupSummaryResponse);
        }

        return PaginatedResponse.of(dtoPage);
    }


    // ------------------------ chức năng dành cho leader nhóm dịch hoặc admin ------------------------ //

    // xoá nhóm dịch, admin cho đc tất, leader chỉ xoá đc nhóm của mình
    @Transactional // cho thêm vì có 1 câu query mình tự định nghĩa, để spring cho vào 1 transaction
    @PreAuthorize("@groupSec.isGroupLeaderOrAdmin(#id)")
    public void softDeleteGroupById(@NonNull String id) {

        TransGroup transGroup = transGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        // Check nhómdịch bị xoá chưa
        if (transGroup.getStatus().equals(TransGroupStatus.DELETED)) {
            throw new AppException(ResponseCode.TRANSGROUP_ALREADY_DELETED);
        }

        // Cập nhập trạng thái nhóm đã bị xoá
        transGroup.setStatus(TransGroupStatus.DELETED);

        Role translatorRole = roleRepository
                .findById(RoleConstants.TRANSLATOR)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));
        // xoá role translator ra khỏi leader
        transGroup.getLeader().getRoles().remove(translatorRole);

        // Dùng native sql xoá role translator ra khỏi các members
        // tránh xoá bằng vòng for gây n+1 query
        userRepository.removeTranslatorRoleFromMembers(id);

        // JPQL xoá members ra khỏi nhóm
        // Leader vẫn sẽ nằm trong nhóm bị xoá
        userRepository.clearTransGroupFromMembers(id);

        transGroupRepository.save(transGroup);
    }

    // cập nhật thông tin group (name và description)
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

        // Nêu request có avatar thì phải cập nhật
        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {

            // xoá avatar cũ của nhóm nếu có
            if (transGroup.getAvatar() != null && !transGroup.getAvatar().isEmpty()) {
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupAvatarPrefix(transGroup.getId()), Instant.now());
            }

            // upload avatar mới của nhóm
            String groupAvatarUrl = storageService.uploadGroupAvatar(transGroup.getId(), request.getAvatarFile());
            // lưu url
            transGroup.setAvatar(groupAvatarUrl);
        }

        // Nêu request có ảnh bìa thì phải cập nhật
        if (request.getCoverFile() != null && !request.getCoverFile().isEmpty()) {

            // xoá ảnh bìa cũ của nhóm nếu có
            if (transGroup.getCoverImageUrl() != null && !transGroup.getCoverImageUrl().isEmpty()) {
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupCoverPrefix(transGroup.getId()), Instant.now());
            }

            String groupCoverUrl = storageService.uploadGroupCover(transGroup.getId(), request.getCoverFile());
            transGroup.setCoverImageUrl(groupCoverUrl);
        }

        return transGroupMapper.toTransGroupResponse(transGroupRepository.save(transGroup));

    }


}
