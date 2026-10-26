package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.SequenceCounterConstants;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaDetailsResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaManagementResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupManagementResponse;
import com.example.mymangaapp.mymangaapp.entity.Category;
import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.MangaMapper;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.CategoryRepository;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageCleanup;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.SlugUtils;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class MangaService {

    MangaRepository mangaRepository;
    TransGroupRepository transGroupRepository;
    CategoryRepository categoryRepository;

    MangaMapper mangaMapper;
    TransGroupMapper transGroupMapper;

    ObjectStorage objectStorage;

    StorageService storageService;
    SequenceCounterService sequenceCounterService;

    StorageCleanup storageCleanup;


    // ----------------------------chức năng dành cho leader nhóm ---------------------------------//

    // tạo mới 1 truyện, dành cho leader
    @Transactional
    public MangaResponse createManga(@NonNull MangaCreationRequest request) {

        String currentUserId = SecurityUtils.getCurrentUserId();

        TransGroup myGroup = transGroupRepository
                .findByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        Set<String> groupIds = new HashSet<>();
        groupIds.add(myGroup.getId()); // mặc định có nhóm của ng đg tạo truyện

        // Nếu có mảng nhóm thì add hết vào, set chống trùng
        if (request.getTransGroups() != null) {
            groupIds.addAll(request.getTransGroups());
        }

        // find tất cả các groups bằng ids và phải đc duyệt hết r
        List<TransGroup> transGroups = transGroupRepository.findAllByIdInAndStatus(groupIds, TransGroupStatus.APPROVED);

        if (transGroups.isEmpty() || transGroups.size() != groupIds.size()) {
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);
        }

        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());

        // check 1 là ko thấy category nào, 2 là có vài category ko tồn tại thì bắn exception
        if (categories.isEmpty() || categories.size() != request.getCategoryIds().size()) {
            throw new AppException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        Manga manga = mangaMapper.toManga(request);
        manga.setCategories(new HashSet<>(categories));
        manga.setTransGroups(new HashSet<>(transGroups));

        // Lấy số thứ tự của truyện
        long sequenceNumber = sequenceCounterService.next(SequenceCounterConstants.MANGA);

        // Tạo slug = name-number
        String slug = SlugUtils.generateSlug(manga.getName(), sequenceNumber);

        manga.setSequenceNumber(sequenceNumber);
        manga.setSlug(slug);

        manga = mangaRepository.save(manga);

        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isEmpty()) {
            // copy từ tmp sang mangas/mangaId/cover/anh-bia.webp
            String coverDestKey = StorageKeysUtils.mangaCover(manga.getId());

            String finalCoverUrl = storageService.promoteTmpFile(request.getCoverImageUrl(), coverDestKey);
            manga.setCoverImageUrl(finalCoverUrl);
        }

        return mangaMapper.toMangaResponse(mangaRepository.save(manga));
    }

    // cập nhật info truyện
    @Transactional
    // check quyền vừa là leader, vừa phải trong nhóm dịch truyện này và vừa nhóm phải APPROVED
    @PreAuthorize("@mangaSec.isLeaderOfManga(#mangaId)")
    public MangaResponse updateMangaById(@NonNull String mangaId, @NonNull MangaUpdateRequest request) {

        Manga manga = mangaRepository
                .findWithDetailsById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        mangaMapper.updateMangaFromRequest(manga, request);

        // Nếu tên truyệnthay đổi thì set lại slug
        if (request.getName() != null && !request.getName().isEmpty()) {
            String slug = SlugUtils.generateSlug(request.getName(), manga.getSequenceNumber());
            manga.setSlug(slug);
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());

            // check 1 là ko thấy category nào, 2 là có vài category ko tồn tại thì bắn exception
            if (categories.isEmpty() || categories.size() != request.getCategoryIds().size()) {
                throw new AppException(ResponseCode.CATEGORY_NOT_FOUND);
            }

            Set<Category> currentCategories = manga.getCategories();
            Set<Category> newCategories = new HashSet<>(categories);

            // Xoá categories ko nằm trong ds mới
            currentCategories.removeIf(category -> !newCategories.contains(category));

            // thêm những categories mới
            currentCategories.addAll(newCategories);
        }

        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isEmpty()) {

            // Nếu có ảnh bìa cũ thì xoá
            if (manga.getCoverImageUrl() != null) {
                String prefix = StorageKeysUtils.mangaCoverPrefix(manga.getId());

                objectStorage.deleteFilesWithPrefix(prefix, Instant.MAX);
            }

            // copy từ tmp sang mangas/mangaId/cover/anh-bia.webp
            String coverDestKey = StorageKeysUtils.mangaCover(manga.getId());

            String finalCoverUrl = storageService.promoteTmpFile(request.getCoverImageUrl(), coverDestKey);

            log.info(coverDestKey);
            log.info(finalCoverUrl);
            manga.setCoverImageUrl(finalCoverUrl);
        }

        addGroups(manga, request.getAddTransGroups());

        return mangaMapper.toMangaResponse(mangaRepository.save(manga));
    }

    // Đây là endpoint dành cho leader nhóm để get manga ra chỉnh sửa
    // trả về response khác so với response trên hàm getMangaById
    public MangaManagementResponse getMangaForManagementById(@NonNull String mangaId) {

        String currentUserId = SecurityUtils.getCurrentUserId();

        // Check có phải user này là leader nhóm dịch truyện này ko (nhóm phải đg hoạt động)
        if (!mangaRepository.isLeaderOfManga(currentUserId, mangaId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        Manga manga = mangaRepository
                .findWithDetailsById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        TransGroup myGroup = transGroupRepository
                .findByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        MangaManagementResponse response = mangaMapper.toMangaManagementResponse(manga);

        List<TransGroup> activeGroups = manga.getTransGroups().stream()
                .filter(transGroup -> transGroup.getStatus().equals(TransGroupStatus.APPROVED))
                .toList();

        // Tính toán mảng các ds nhóm trả về xem có cờ xem leader hiện tại có thể xoá nhóm đó không
        List<TransGroupManagementResponse> responses = activeGroups.stream()
                .map(transGroup -> {
                    TransGroupManagementResponse res = transGroupMapper.toTransGroupManagementResponse(transGroup);

                    // Nhóm dịch được xoá khi chính user hiện tại là leader nhóm đó
                    // và phải có từ 2 nhóm trở lên đang dịch truyện này
                    boolean isRemovable = transGroup.getId().equals(myGroup.getId()) && activeGroups.size() > 1;
                    res.setCanRemove(isRemovable);

                    return res;
                })
                .toList();

        response.setTransGroups(responses);

        return response;
    }


    // ----------------------------chức năng dành cho admin hoặc leader nhóm-----------------------------------

    // xoá truyện theo id
    @Transactional
    public void deleteMangaById(@NonNull String mangaId) {

        Manga manga = mangaRepository
                .findForUpdateById(mangaId) // dùng method có lock để tránh ghi cùng lúc
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        String currentUserId = SecurityUtils.getCurrentUserId();
        // chỉ admin hoặc leader của bất kỳ nhóm dịch truyện này mới cho qua
        if (!SecurityUtils.isAdmin() && !mangaRepository.isLeaderOfManga(currentUserId, manga.getId(), TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        mangaRepository.deletePagesById(manga.getId());
        mangaRepository.deleteChaptersById(manga.getId());
        mangaRepository.deleteCategoryLinks(manga.getId());
        mangaRepository.deleteGroupLinks(manga.getId());
        mangaRepository.deleteRowById(manga.getId());

        storageCleanup.deletePrefixAfterCommit(StorageKeysUtils.mangaPrefix(manga.getId()));
    }


    // -------------------------chức năng chỉ dành cho admin --------------------------------//

    // chỉ dành cho admin
    public PaginatedResponse<MangaSummaryResponse> getMangas(
            MangaStatus status, int page,
            int size, String sortBy
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<MangaSummaryResponse> dtoPage;

        if (status != null) {
            dtoPage = mangaRepository
                    .findAllByStatus(status, pageable)
                    .map(mangaMapper::toMangaSummaryResponse);
        } else {
            dtoPage = mangaRepository
                    .findAll(pageable)
                    .map(mangaMapper::toMangaSummaryResponse);
        }

        return PaginatedResponse.of(dtoPage);
    }


    // -----------------------------chức năng public (cho khách) --------------------------------//

    // lấy tất manga, public có pagination
    // đây là khi họ vào trang home của web
    // sẽ lấy tất cả các manga có phân trang, những chỉ lấy với
    // chút thông tin của manga như: name, categories, description, transgroups
    public PaginatedResponse<MangaResponse> getMangas(int page, int size, String sortBy) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<MangaResponse> dtoPage = mangaRepository
                .findAll(pageable)
                .map(mangaMapper::toMangaResponse);

        return PaginatedResponse.of(dtoPage);
    }

    // public
    // đây là endpoint khi user vào trang của nhóm dịch
    // thì lấy tất cả các bộ truyện mà nhóm đang tham gia dịch
    // cả dịch chính lẫn phụ
    public PaginatedResponse<MangaSummaryResponse> getMangasByGroupId(
            @NonNull String groupId, int page,
            int size, @NonNull String sortBy
    ) {

        if (!transGroupRepository.existsById(groupId)) {
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<MangaSummaryResponse> dtoPage = mangaRepository
                .findAllByGroupMembershipId(groupId, pageable)
                .map(mangaMapper::toMangaSummaryResponse);

        return PaginatedResponse.of(dtoPage);
    }

    // public
    // đây là khi user nhấn chi tiết 1 manga
    // manga sẽ get toàn bộ info, có cả các chapter luôn
    public MangaDetailsResponse getMangaByIdOrSlug(@NonNull String idOrSlug) {

        // này phải dùng method select toàn bộ thông tin manga có cả chapter
        Manga manga = UUIDUtils.isUuid(idOrSlug)
                ? mangaRepository
                        .findWithDetailsById(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND))
                : mangaRepository
                        .findWithDetailsBySlug(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        return mangaMapper.toMangaDetailsResponse(manga);
    }


    // --------------------------method private --------------------------------------

    private void addGroups(Manga manga, Set<String> addIds) {
        if (addIds == null || addIds.isEmpty())
            return;

        // lấy nhóm đang dịch truyện này
        Set<String> existingIds = manga.getTransGroups()
                .stream()
                .map(TransGroup::getId) // <=> transgroup -> transgroup.getId()
                .collect(Collectors.toSet());

        // đây là các nhóm dịch mới thêm
        Set<String> newIds = new HashSet<>(addIds);
        newIds.removeAll(existingIds); // xoá nhóm cũ đi

        if (newIds.isEmpty())
            return;

        List<TransGroup> newTransGroups = transGroupRepository
                .findAllByIdInAndStatus(newIds, TransGroupStatus.APPROVED);

        if (newTransGroups.size() != newIds.size())
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);

        // add tất cả nhóm mới vào
        manga.getTransGroups().addAll(newTransGroups);
    }

}
