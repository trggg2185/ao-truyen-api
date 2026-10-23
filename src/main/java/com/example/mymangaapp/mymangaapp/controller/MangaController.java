package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.response.*;
import com.example.mymangaapp.mymangaapp.enums.LimitType;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.service.MangaService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MangaController {

    MangaService mangaService;


    // ---------------------------------endpoints dành cho leader nhóm hoặc admin ------------------------------//

    @PostMapping("/mangas")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<MangaResponse> createManga(
            @Valid @RequestBody MangaCreationRequest request
    ) {
        MangaResponse response = mangaService.createManga(request);

        return ApiResponse.<MangaResponse>builder()
                .result(response)
                .build();
    }

    @PatchMapping("/mangas/{mangaId}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<MangaResponse> updateMangaById(
            @PathVariable @NonNull String mangaId,
            @Valid @RequestBody MangaUpdateRequest request
    ) {
        MangaResponse response = mangaService.updateMangaById(mangaId, request);

        return ApiResponse.<MangaResponse>builder()
                .result(response)
                .build();
    }

    @DeleteMapping("/transgroups/{groupId}/mangas/{mangaId}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<DeleteMangaResponse> deleteMangaById(
            @PathVariable @NonNull String groupId,
            @PathVariable @NonNull String mangaId
    ) {

        mangaService.deleteMangaById(groupId, mangaId);

        return ApiResponse.<DeleteMangaResponse>builder()
                .message(ResponseCode.SUCCESS.getMessage())
                .result(DeleteMangaResponse.builder().groupId(groupId).mangaId(mangaId).build())
                .build();
    }

    @GetMapping("/mangas/{mangaId}/management")
    @RateLimit(capacity = 60)
    public ApiResponse<MangaManagementResponse> getMangaForManagementById(@PathVariable @NonNull String mangaId) {
        MangaManagementResponse response = mangaService.getMangaForManagementById(mangaId);

        return ApiResponse.<MangaManagementResponse>builder()
                .result(response)
                .build();
    }


    // ---------------------------------endpoints chỉ cho admin ------------------------------//

    // admin only
    @GetMapping("/admin/mangas")
    @RateLimit(capacity = 30, limitType = LimitType.USER_ID)
    public ApiResponse<PaginatedResponse<MangaSummaryResponse>> getMangas(
            @RequestParam(required = false) MangaStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        PaginatedResponse<MangaSummaryResponse> responses = mangaService.getMangas(status, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<MangaSummaryResponse>>builder()
                .result(responses)
                .build();
    }


    // ---------------------------------endpoints public (cho khách) ---------------------------//

    // lấy tất manga public có pagination
    // đây là khi họ vào trang home của web
    // sẽ lấy tất cả các manga có phân trang, những chỉ lấy với
    // chút thông tin của manga như: name, categories, description, transgroups
    @GetMapping("/mangas")
    @RateLimit(capacity = 60)
    public ApiResponse<PaginatedResponse<MangaResponse>> getMangas(
            @RequestParam(defaultValue = "0") int page, // số trang
            @RequestParam(defaultValue = "24") int size, // số bản ghi mỗi trang
            @RequestParam(defaultValue = "createdAt") String sortBy // sxep theo field nào
    ) {
        PaginatedResponse<MangaResponse> responses = mangaService.getMangas(page, size, sortBy);

        return ApiResponse.<PaginatedResponse<MangaResponse>>builder()
                .result(responses)
                .build();
    }

    // public
    // đây là endpoint khi user vào trang của nhóm dịch
    // thì lấy tất cả các bộ truyện mà nhóm đang tham gia dịch
    // cả dịch chính lẫn phụ
    @GetMapping("/transgroups/{groupId}/mangas")
    @RateLimit(capacity = 60)
    public ApiResponse<PaginatedResponse<MangaSummaryResponse>> getMangasByGroupId(
            @PathVariable @NonNull String groupId,
            @RequestParam(defaultValue = "0") int page, // số trang
            @RequestParam(defaultValue = "15") int size, // số bản ghi mỗi trang
            @RequestParam(defaultValue = "createdAt") String sortBy // sxep theo field nào
    ) {
        PaginatedResponse<MangaSummaryResponse> responses = mangaService.getMangasByGroupId(groupId, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<MangaSummaryResponse>>builder()
                .result(responses)
                .build();
    }

    @GetMapping("/mangas/{idOrSlug}")
    @RateLimit(capacity = 60)
    public ApiResponse<MangaDetailsResponse> getMangaByIdOrSlug(
            @PathVariable @NonNull String idOrSlug
    ) {
        MangaDetailsResponse response = mangaService.getMangaByIdOrSlug(idOrSlug);

        return ApiResponse.<MangaDetailsResponse>builder()
                .result(response)
                .build();
    }

}
