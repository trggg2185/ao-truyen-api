package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.groupjoin.response.JoinRequestResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupJoinRequest;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.enums.GroupJoinRequestStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.GroupJoinRequestMapper;
import com.example.mymangaapp.mymangaapp.repository.GroupJoinRequestRepository;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional(readOnly = true)
public class GroupJoinRequestService {

    RoleRepository roleRepository;
    UserRepository userRepository;
    TransGroupRepository transGroupRepository;
    GroupJoinRequestRepository groupJoinRequestRepository;
    GroupJoinRequestMapper groupJoinRequestMapper;

    // ---------------------------------------chức năng cho user đã đăng nhập ---------------------------------------//

    @Transactional
    public JoinRequestResponse requestJoinGroup(@NonNull String groupId) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        User user = userRepository
                .findWithGroupById(currentUserId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (groupJoinRequestRepository.existsByTransGroupIdAndUserIdAndStatus(groupId, user.getId(), GroupJoinRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_JOIN_REQUEST_ALREADY_EXISTED);
        }

        if (user.getTransGroup() != null || transGroupRepository.existsByLeaderIdAndStatus(user.getId(), TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.USER_ALREADY_IN_GROUP);
        }

        TransGroup transGroup = transGroupRepository
                .findWithLeaderAndMembersByIdAndStatus(groupId, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        GroupJoinRequest groupJoinRequest = GroupJoinRequest
                .builder()
                .transGroup(transGroup)
                .user(user)
                .build();

        return groupJoinRequestMapper.toJoinRequestResponse(groupJoinRequestRepository.save(groupJoinRequest));
    }

    // Lấy danh sách yêu cầu gia nhập nhóm của chính user hiện tại bằng DTO-based Projection
    public List<JoinRequestResponse> getMyJoinRequests(GroupJoinRequestStatus status) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        return groupJoinRequestRepository.findResponses(currentUserId, null, status);
    }

    // ---------------------------------------chức năng cho leader nhóm dịch ---------------------------------------//

    // Lấy danh sách yêu cầu xin vào nhóm (leader only) bằng DTO-based Projection
    @PreAuthorize("@groupSec.isGroupLeader(#groupId)")
    public List<JoinRequestResponse> getJoinRequests(GroupJoinRequestStatus status, @NonNull String groupId) {
        return groupJoinRequestRepository.findResponses(null, groupId, status);
    }

    @PreAuthorize("@groupSec.isGroupLeader(#groupId)")
    @Transactional
    public JoinRequestResponse approveJoinGroup(@NonNull String groupId, @NonNull String requestId) {
        GroupJoinRequest groupJoinRequest = groupJoinRequestRepository
                .findWithDetailsById(requestId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_JOIN_REQUEST_NOT_FOUND));

        TransGroup transGroup = groupJoinRequest.getTransGroup();
        if (transGroup.getStatus().equals(TransGroupStatus.DELETED)) {
            throw new AppException(ResponseCode.TRANSGROUP_ALREADY_DELETED);
        }

        User user = groupJoinRequest.getUser();

        if (!transGroup.getId().equals(groupId)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        if (user.getTransGroup() != null) {
            throw new AppException(ResponseCode.USER_ALREADY_IN_GROUP);
        }

        if (!groupJoinRequest.getStatus().equals(GroupJoinRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_JOIN_REQUEST_STATUS_INVALID);
        }

        Role translatorRole = roleRepository
                .findById(RoleConstants.TRANSLATOR)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));

        GroupJoinRequest approvedRequest = groupJoinRequestRepository
                .findByTransGroupIdAndUserIdAndStatus(
                        transGroup.getId(),
                        user.getId(),
                        GroupJoinRequestStatus.APPROVED
                )
                .orElse(null);

        user.setTransGroup(transGroup);
        user.getRoles().add(translatorRole);
        transGroup.getMembers().add(user);

        userRepository.save(user);
        transGroupRepository.save(transGroup);

        if (approvedRequest != null) {
            groupJoinRequestRepository.delete(groupJoinRequest);
            return groupJoinRequestMapper.toJoinRequestResponse(approvedRequest);
        }

        groupJoinRequest.setStatus(GroupJoinRequestStatus.APPROVED);

        return groupJoinRequestMapper.toJoinRequestResponse(
                groupJoinRequestRepository.save(groupJoinRequest)
        );
    }

    @PreAuthorize("@groupSec.isGroupLeader(#groupId)")
    @Transactional
    public JoinRequestResponse rejectJoinGroup(@NonNull String groupId, @NonNull String requestId) {
        GroupJoinRequest groupJoinRequest = groupJoinRequestRepository
                .findWithDetailsById(requestId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_JOIN_REQUEST_NOT_FOUND));

        TransGroup transGroup = groupJoinRequest.getTransGroup();

        if (!transGroup.getId().equals(groupId)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        if (!groupJoinRequest.getStatus().equals(GroupJoinRequestStatus.PENDING)) {
            throw new AppException(ResponseCode.TRANSGROUP_JOIN_REQUEST_STATUS_INVALID);
        }

        groupJoinRequest.setStatus(GroupJoinRequestStatus.REJECTED);

        return groupJoinRequestMapper.toJoinRequestResponse(
                groupJoinRequestRepository.save(groupJoinRequest)
        );
    }

}
