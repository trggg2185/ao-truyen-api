package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.SequenceCounterConstants;
import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaManagementResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupManagementResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.Category;
import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.MangaMapper;
import com.example.mymangaapp.mymangaapp.repository.CategoryRepository;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.projection.MangaCategoryProjection;
import com.example.mymangaapp.mymangaapp.repository.projection.MangaTransGroupProjection;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageCleanup;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class MangaService {

    MangaRepository mangaRepository;
    TransGroupRepository transGroupRepository;
    CategoryRepository categoryRepository;

    MangaMapper mangaMapper;

    ObjectStorage objectStorage;
    StorageService storageService;
    SequenceCounterService sequenceCounterService;
    StorageCleanup storageCleanup;

    // ----------------------------chức năng dành cho leader nhóm ---------------------------------//

    @Transactional
    public MangaResponse createManga(@NonNull MangaCreationRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        TransGroup myGroup = transGroupRepository
                .findByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        Set<String> groupIds = new HashSet<>();
        groupIds.add(myGroup.getId());

        if (request.getTransGroups() != null) {
            groupIds.addAll(request.getTransGroups());
        }

        List<TransGroup> transGroups = transGroupRepository.findAllByIdInAndStatus(groupIds, TransGroupStatus.APPROVED);
        if (transGroups.isEmpty() || transGroups.size() != groupIds.size()) {
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);
        }

        List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
        if (categories.isEmpty() || categories.size() != request.getCategoryIds().size()) {
            throw new AppException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        Manga manga = mangaMapper.toManga(request);
        manga.setCategories(new HashSet<>(categories));
        manga.setTransGroups(new HashSet<>(transGroups));

        long sequenceNumber = sequenceCounterService.next(SequenceCounterConstants.MANGA);
        String slug = SlugUtils.generateSlug(manga.getName(), sequenceNumber);

        manga.setSequenceNumber(sequenceNumber);
        manga.setSlug(slug);
        manga = mangaRepository.save(manga);

        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isEmpty()) {
            String coverDestKey = StorageKeysUtils.mangaCover(manga.getId());
            String finalCoverUrl = storageService.promoteTmpFile(request.getCoverImageUrl(), coverDestKey);
            manga.setCoverImageUrl(finalCoverUrl);
        }

        return mangaMapper.toMangaResponse(mangaRepository.save(manga));
    }

    @Transactional
    @PreAuthorize("@mangaSec.isLeaderOfManga(#mangaId)")
    public MangaResponse updateMangaById(@NonNull String mangaId, @NonNull MangaUpdateRequest request) {
        Manga manga = mangaRepository
                .findWithDetailsById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        mangaMapper.updateMangaFromRequest(manga, request);

        if (request.getName() != null && !request.getName().isEmpty()) {
            String slug = SlugUtils.generateSlug(request.getName(), manga.getSequenceNumber());
            manga.setSlug(slug);
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
            if (categories.isEmpty() || categories.size() != request.getCategoryIds().size()) {
                throw new AppException(ResponseCode.CATEGORY_NOT_FOUND);
            }

            Set<Category> currentCategories = manga.getCategories();
            Set<Category> newCategories = new HashSet<>(categories);
            currentCategories.removeIf(category -> !newCategories.contains(category));
            currentCategories.addAll(newCategories);
        }

        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isEmpty()) {
            if (manga.getCoverImageUrl() != null) {
                String prefix = StorageKeysUtils.mangaCoverPrefix(manga.getId());
                objectStorage.deleteFilesWithPrefix(prefix, Instant.MAX);
            }

            String coverDestKey = StorageKeysUtils.mangaCover(manga.getId());
            String finalCoverUrl = storageService.promoteTmpFile(request.getCoverImageUrl(), coverDestKey);
            manga.setCoverImageUrl(finalCoverUrl);
        }

        addGroups(manga, request.getAddTransGroups());

        return mangaMapper.toMangaResponse(mangaRepository.save(manga));
    }

    /*
     * Lấy chi tiết truyện cho Leader chỉnh sửa (GET /mangas/{mangaId}/management):
     * Kết hợp DTO-based Projection (findManagementById) và Interface-based Projection
     * (findCategoryProjectionsByMangaIds + findTransGroupProjectionsByMangaIds) thay vì dùng @EntityGraph.
     */
    public MangaManagementResponse getMangaForManagementById(@NonNull String mangaId) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        if (!mangaRepository.isLeaderOfManga(currentUserId, mangaId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        MangaManagementResponse response = mangaRepository
                .findManagementById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        TransGroupSummaryResponse myGroup = transGroupRepository
                .findSummaryByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        Set<CategoryResponse> categories = mangaRepository
                .findCategoryProjectionsByMangaIds(List.of(mangaId))
                .stream()
                .map(p -> new CategoryResponse(p.getCategoryId(), p.getName(), p.getDescription()))
                .collect(Collectors.toSet());
        response.setCategories(categories);

        List<MangaTransGroupProjection> activeGroups = mangaRepository
                .findTransGroupProjectionsByMangaIds(List.of(mangaId))
                .stream()
                .filter(g -> g.getStatus() == TransGroupStatus.APPROVED)
                .toList();

        List<TransGroupManagementResponse> groupResponses = activeGroups.stream()
                .map(g -> TransGroupManagementResponse.builder()
                        .id(g.getGroupId())
                        .name(g.getName())
                        .slug(g.getSlug())
                        .canRemove(g.getGroupId().equals(myGroup.getId()) && activeGroups.size() > 1)
                        .build())
                .toList();

        response.setTransGroups(groupResponses);
        return response;
    }

    // ----------------------------chức năng dành cho admin hoặc leader nhóm-----------------------------------

    @Transactional
    public void deleteMangaById(@NonNull String mangaId) {
        Manga manga = mangaRepository
                .findForUpdateById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        String currentUserId = SecurityUtils.getCurrentUserId();

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

    // Dùng DTO-based Projection truy vấn thẳng ra Page<MangaSummaryResponse>
    public PaginatedResponse<MangaSummaryResponse> getMangas(
            MangaStatus status, int page,
            int size, String sortBy
    ) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, Sort.Direction.DESC);
        Page<MangaSummaryResponse> dtoPage = mangaRepository.findSummaries(status, pageable);
        return PaginatedResponse.of(dtoPage);
    }

    // -----------------------------chức năng public (cho khách) --------------------------------//

    /*
     * Lấy danh sách truyện trang chủ có phân trang (GET /mangas):
     * 1. Gọi findPublicPage(pageable) dùng DTO-based Projection lấy đúng 24 dòng MangaResponse dưới DB.
     * 2. Gọi populateRelations(...) dùng Interface-based Projection nạp toàn bộ categories và transGroups
     *    của 24 bộ truyện đó chỉ bằng 2 câu query `WHERE m.id IN (...)`.
     */
    public PaginatedResponse<MangaResponse> getMangas(int page, int size, String sortBy) {
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, Sort.Direction.DESC);

        Page<MangaResponse> dtoPage = mangaRepository.findPublicPage(pageable);
        populateRelations(dtoPage.getContent());

        return PaginatedResponse.of(dtoPage);
    }

    // trang home sẽ gọi endpoint này
    // Lấy ds 20 truyện tranh mới cập nhật các chap mới
    public List<MangaResponse> getTop20RecentlyUpdatedMangas() {

        List<MangaResponse> responses = mangaRepository
                .findRecentlyUpdatedMangas(PageRequest.of(0, 20));

        // Nạp các thể loại truyện và các nhóm dịch
        populateRelations(responses);

        return responses;
    }

    public List<MangaResponse> getTop10NewlyMangas() {
        List<MangaResponse> responses = mangaRepository
                .findNewlyMangas(PageRequest.of(0, 10));

        // Nạp các thể loại truyện và các nhóm dịch
        populateRelations(responses);

        return responses;
    }

    // Lấy danh sách các bộ truyện mà nhóm đang tham gia dịch bằng DTO-based Projection
    public PaginatedResponse<MangaSummaryResponse> getMangasByGroupId(
            @NonNull String groupId, int page,
            int size, @NonNull String sortBy
    ) {
        if (!transGroupRepository.existsById(groupId)) {
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);
        }

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, Sort.Direction.DESC);

        Page<MangaSummaryResponse> dtoPage = mangaRepository
                .findSummariesByGroupId(groupId, pageable);

        return PaginatedResponse.of(dtoPage);
    }

    /*
     * Xem chi tiết 1 bộ truyện theo ID hoặc Slug (GET /mangas/{idOrSlug}):
     * Đã xoá MangaDetailsResponse dư thừa và hợp nhất vào MangaResponse.
     * Dùng DTO-based Projection kết hợp Interface-based Projection để không tạo Cartesian Product.
     */
    public MangaResponse getMangaByIdOrSlug(@NonNull String idOrSlug) {
        MangaResponse response = UUIDUtils.isUuid(idOrSlug)
                ? mangaRepository
                        .findProjectedById(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND))
                : mangaRepository
                        .findProjectedBySlug(idOrSlug)
                        .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        populateRelations(List.of(response));
        return response;
    }

    // --------------------------method private --------------------------------------

    /*
     * Hàm phụ trợ nạp batch categories và transGroups cho danh sách MangaResponse
     * thông qua 2 Interface-based Projection (MangaCategoryProjection & MangaTransGroupProjection).
     *
     * Cách hoạt động:
     * - Thu thập toàn bộ mangaIds từ danh sách DTO.
     * - Bắn 1 câu SQL lấy tất cả cặp (mangaId, Category) -> gom nhóm vào Map<String, Set<CategoryResponse>>.
     * - Bắn 1 câu SQL lấy tất cả cặp (mangaId, TransGroup) -> gom nhóm vào Map<String, List<TransGroupSummaryResponse>>.
     * - Gán ngược vào từng phần tử MangaResponse trong bộ nhớ với độ phức tạp O(1) tra cứu Map.
     */
    private void populateRelations(List<MangaResponse> mangas) {
        if (mangas.isEmpty()) {
            return;
        }

        List<String> mangaIds = mangas.stream()
                .map(MangaResponse::getId)
                .toList();

        Map<String, Set<CategoryResponse>> categoriesByMangaId = mangaRepository
                .findCategoryProjectionsByMangaIds(mangaIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MangaCategoryProjection::getMangaId,
                        Collectors.mapping(
                                p -> new CategoryResponse(p.getCategoryId(), p.getName(), p.getDescription()),
                                Collectors.toSet()
                        )
                ));

        Map<String, List<TransGroupSummaryResponse>> groupsByMangaId = mangaRepository
                .findTransGroupProjectionsByMangaIds(mangaIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MangaTransGroupProjection::getMangaId,
                        Collectors.mapping(
                                p -> new TransGroupSummaryResponse(p.getGroupId(), p.getName(), p.getSlug(), p.getAvatar()),
                                Collectors.toList()
                        )
                ));

        for (MangaResponse manga : mangas) {
            manga.setCategories(categoriesByMangaId.getOrDefault(manga.getId(), Collections.emptySet()));
            manga.setTransGroups(groupsByMangaId.getOrDefault(manga.getId(), Collections.emptyList()));
        }
    }

    private void addGroups(Manga manga, Set<String> addIds) {
        if (addIds == null || addIds.isEmpty())
            return;

        Set<String> existingIds = manga.getTransGroups()
                .stream()
                .map(TransGroup::getId)
                .collect(Collectors.toSet());

        Set<String> newIds = new HashSet<>(addIds);
        newIds.removeAll(existingIds);

        if (newIds.isEmpty())
            return;

        List<TransGroup> newTransGroups = transGroupRepository
                .findAllByIdInAndStatus(newIds, TransGroupStatus.APPROVED);

        if (newTransGroups.size() != newIds.size())
            throw new AppException(ResponseCode.TRANSGROUP_NOT_FOUND);

        manga.getTransGroups().addAll(newTransGroups);
    }

}
