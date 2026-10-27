package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.groupcreation.request.CreationRequest;
import com.example.mymangaapp.mymangaapp.dto.groupcreation.response.CreationRequestResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupCreationRequest;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.GroupCreationRequestMapper;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.GroupCreationRequestRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class GroupCreationRequestService {

    GroupCreationRequestRepository groupCreationRequestRepository;
    TransGroupRepository transGroupRepository;
    UserRepository userRepository;
    GroupCreationRequestMapper groupCreationRequestMapper;
    TransGroupMapper transGroupMapper;
    TransGroupService transGroupService;

    // -----------------------------chức năng dành cho user đã đăng nhập -------------------------------------

    @Transactional
    public CreationRequestResponse requestCreateGroup(@NonNull CreationRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        if (groupCreationRequestRepository.existsByCreatorIdAndStatus(currentUserId, GroupCreationRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_CREATION_REQUEST_ALREADY_EXISTED);
        }

        User user = userRepository
                .findById(currentUserId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        GroupCreationRequest groupCreationRequest = groupCreationRequestMapper.toGroupCreationRequest(request);
        groupCreationRequest.setCreator(user);

        return groupCreationRequestMapper.toCreationRequestResponse(
                groupCreationRequestRepository.save(groupCreationRequest)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CreationRequestResponse> getMyCreationRequests(
            GroupCreationRequestStatus status,
            int page,
            int size,
            @NonNull String sortBy) {

        String currentUserId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        // Dùng DTO-based Projection: truy vấn thẳng ra Page<CreationRequestResponse>, không nạp Entity User
        Page<CreationRequestResponse> dtoPage = groupCreationRequestRepository
                .findResponses(currentUserId, status, pageable);

        return PaginatedResponse.of(dtoPage);
    }

    // ----------------------------------chức năng cho admin ----------------------------------------

    @Transactional
    public TransGroupResponse approveCreateGroup(@NonNull String requestId) {
        GroupCreationRequest groupCreationRequest = groupCreationRequestRepository
                .findWithCreatorById(requestId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_CREATION_REQUEST_NOT_FOUND));

        if (!groupCreationRequest.getStatus().equals(GroupCreationRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_CREATION_REQUEST_STATUS_INVALID);
        }

        User creator = groupCreationRequest.getCreator();

        boolean isLeadingActiveGroup =
                transGroupRepository.existsByLeaderIdAndStatus(creator.getId(), TransGroupStatus.APPROVED);

        if (creator.getTransGroup() != null || isLeadingActiveGroup) {
            throw new AppException(ResponseCode.USER_ALREADY_IN_GROUP);
        }

        User reviewer = userRepository
                .findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        groupCreationRequest.setStatus(GroupCreationRequestStatus.APPROVED);

        TransGroup createdGroup = transGroupService.createTransGroup(groupCreationRequest);

        groupCreationRequest.setCreatedGroup(createdGroup);
        groupCreationRequest.setReviewer(reviewer);

        groupCreationRequestRepository.save(groupCreationRequest);

        return transGroupMapper.toTransGroupResponse(createdGroup);
    }

    @Transactional
    public CreationRequestResponse rejectCreateGroup(@NonNull String requestId) {
        GroupCreationRequest groupCreationRequest = groupCreationRequestRepository
                .findWithCreatorById(requestId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_CREATION_REQUEST_NOT_FOUND));

        if (!groupCreationRequest.getStatus().equals(GroupCreationRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_CREATION_REQUEST_STATUS_INVALID);
        }

        User reviewer = userRepository
                .findByUsername(SecurityUtils.getCurrentUsername())
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        groupCreationRequest.setStatus(GroupCreationRequestStatus.REJECTD);
        groupCreationRequest.setReviewer(reviewer);

        return groupCreationRequestMapper.toCreationRequestResponse(
                groupCreationRequestRepository.save(groupCreationRequest)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CreationRequestResponse> getAllCreationRequests(
            GroupCreationRequestStatus status, int page,
            int size, @NonNull String sortBy
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        // Truyền creatorId = null để lấy yêu cầu của mọi user, có thể lọc theo status
        Page<CreationRequestResponse> dtoPage = groupCreationRequestRepository
                .findResponses(null, status, pageable);

        return PaginatedResponse.of(dtoPage);
    }

}
