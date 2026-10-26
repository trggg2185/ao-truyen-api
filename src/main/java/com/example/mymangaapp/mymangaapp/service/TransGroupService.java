package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupDetailsResponse;
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
import com.example.mymangaapp.mymangaapp.mapper.MangaMapper;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.*;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import com.example.mymangaapp.mymangaapp.utils.UUIDUtils;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j @Transactional(readOnly = true)
public class TransGroupService {

    UserRepository userRepository;
    TransGroupRepository transGroupRepository;
    RoleRepository roleRepository;
    GroupCreationRequestRepository groupCreationRequestRepository;
    GroupJoinRequestRepository groupJoinRequestRepository;
    MangaRepository mangaRepository;

    TransGroupMapper transGroupMapper;
    MangaMapper mangaMapper;

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

        // Nếu ko phải là admin yêu cầu tạo nhóm thì mới gán role translator
        if (!userRepository.hasRole(creator.getId(), RoleConstants.ADMIN)) {
            // Gán role translator cho leader
            creator.getRoles().add(translatorRole);
        }

        // là admin yc tạo nhóm thì save luôn, chỉ được có 1 role admin thôi
        creator = userRepository.save(creator);

        // quyết định là trong members sẽ ko chứa leader
        TransGroup transGroup = TransGroup.builder()
                .name(groupCreationRequest.getGroupName())
                .description(groupCreationRequest.getDescription())
                .leader(creator)
                .build();

        return transGroupRepository.save(transGroup);
    }


    // ------------------------------------chức năng cho user đã đăng nhập ----------------------------------//

    // Chức năng lấy nhóm dịch mà user đang tham gia
    public TransGroupOfUserResponse getMyGroup() {

        // Nếu chưa đăng nhập thì tự ném UNAUTHENTICATED
        // Frontend sẽ biết yêu cầu user đăng nhập
        String currentUserId = SecurityUtils.getCurrentUserId();

        User me = userRepository
                .findWithGroupById(currentUserId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        // lấy nhóm user đang tham gia (ko phải leader nhé)
        TransGroup myJoinedGroup = me.getTransGroup();

        // check user đang là member của nhóm nào trước
        if (myJoinedGroup != null && myJoinedGroup.getStatus().equals(TransGroupStatus.APPROVED)) {
            return TransGroupOfUserResponse.builder()
                    .transGroup(transGroupMapper.toTransGroupSummaryResponse(myJoinedGroup))
                    .status(MyGroupStatus.MEMBER)
                    .build();
        }

        // nếu ko phải member thì tìm tiếp xem có phải leader của nhóm ko
        Optional<TransGroup> myGroupOpt = transGroupRepository
                .findByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED);

        // nếu user có leader của group nào thì trả về
        if (myGroupOpt.isPresent()) {
            return TransGroupOfUserResponse.builder()
                    .transGroup(transGroupMapper.toTransGroupSummaryResponse(myGroupOpt.get()))
                    .status(MyGroupStatus.LEADER)
                    .build();
        }

        // nếu cũng ko phải leader nhóm nào thì tìm tiếp xem
        // user có đang gửi yc tạo nhóm hay tham gia nhóm mà đang chờ duyệt không
        // thì trả về response
        if (groupCreationRequestRepository.existsByCreatorIdAndStatus(currentUserId, GroupCreationRequestStatus.PENDING) ||
            groupJoinRequestRepository.existsByUserIdAndStatus(currentUserId, GroupJoinRequestStatus.PENDING)
        ) {
            return TransGroupOfUserResponse.builder()
                    .status(MyGroupStatus.PENDING_REQUEST)
                    .build();
        }

        // nếu ko phải tất cả trường hợp trên thì trả về ko tham gia nhóm dịch nào
        return TransGroupOfUserResponse.builder()
                .status(MyGroupStatus.NONE)
                .build();
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
    public TransGroupDetailsResponse getGroupByIdOrSlug(@NonNull String idOrSlug) {

        TransGroup transGroup = UUIDUtils.isUuid(idOrSlug)
                ? transGroupRepository // là id thì tìm theo id
                        .findWithDetailsById(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND))
                : transGroupRepository // là slug thì tìm theo slug
                        .findWithDetailsBySlug(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        List<Manga> translatedMangas = mangaRepository
                .findAllByGroupId(transGroup.getId());

        TransGroupDetailsResponse response = transGroupMapper.toTransGroupDetailsResponse(transGroup);
        response.setTranslatedMangas(new HashSet<>(
                translatedMangas.stream().map(mangaMapper::toMangaSummaryResponse).toList()
        ));

        return response;
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
    public void softDeleteGroupById(@NonNull String id) {

        TransGroup transGroup = transGroupRepository
                .findWithLeaderAndRolesById(id) // get leader và roles của leader luôn tí dùng
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        // Check nhómdịch bị xoá chưa
        if (transGroup.getStatus().equals(TransGroupStatus.DELETED)) {
            throw new AppException(ResponseCode.TRANSGROUP_ALREADY_DELETED);
        }

        boolean isAdmin = SecurityUtils.isAdmin();
        boolean isLeader = SecurityUtils.getCurrentUserId().equals(transGroup.getLeader().getId());
        // ko phải admin hoặc leader thì ko có quyền !(isAdmin || isLeader)
        if (!isAdmin && !isLeader) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        // Cập nhập trạng thái nhóm đã bị xoá
        transGroup.setStatus(TransGroupStatus.DELETED);

        // Tìm những truyện mà nếu nhóm dịch này bị delete thì truyện sẽ ko còn nhóm nào dịch nx
        List<String> orphanMangaIds = mangaRepository
                .findMangaIdsWhereGroupIsLastActive(id, TransGroupStatus.APPROVED);

        // Nếu có truyện mà nhóm delete thì truyện mô cồi...
        if (!orphanMangaIds.isEmpty()) {
            // Nếu là leader xoá nhóm thì ko cho vì nếu delete thì các truyện có
            // mỗi nhóm này dịch mồ côi hết
            if (isLeader) throw new AppException(ResponseCode.TRANSGROUP_IS_THE_LAST_GROUP_OF_MANGA);

            // nếu là admin xoá nhóm thì log cảnh báo
            log.warn("Admin xoá nhóm {} làm {} truyện không còn nhóm nào dịch: {}",
                    id,
                    orphanMangaIds.size(),
                    orphanMangaIds);
        }

        // Nếu ko có truyện nào mô côi sau khi nhóm delete thì cứ thế mà triển

        // Dùng native sql xoá role translator ra khỏi các members
        // tránh xoá bằng vòng for gây n+1 query
        userRepository.removeTranslatorRoleFromMembers(id);

        // JPQL xoá members ra khỏi nhóm
        // Leader vẫn sẽ nằm trong nhóm bị xoá
        userRepository.clearTransGroupFromMembers(id);

        // những yêu cầu nào đang xin vào nhóm này thì tự reject hết
        groupJoinRequestRepository.rejectPendingByGroupId(id);

        // xoá role translator ra khỏi leader
        transGroup.getLeader()
                .getRoles().removeIf(role -> RoleConstants.TRANSLATOR.equals(role.getName()));
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
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupAvatarPrefix(transGroup.getId()), Instant.MAX);
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
                objectStorage.deleteFilesWithPrefix(StorageKeysUtils.groupCoverPrefix(transGroup.getId()), Instant.MAX);
            }

            String groupCoverUrl = storageService.uploadGroupCover(transGroup.getId(), request.getCoverFile());
            transGroup.setCoverImageUrl(groupCoverUrl);
        }

        return transGroupMapper.toTransGroupResponse(transGroupRepository.save(transGroup));

    }


}
