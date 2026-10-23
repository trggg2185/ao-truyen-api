package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.*;
import com.example.mymangaapp.mymangaapp.enums.LimitType;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.service.TransGroupService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransGroupController {

    TransGroupService transGroupService;


    // -----------------------------------endpoint cho user đã đăng nhập ----------------------------------//

    @GetMapping("/users/me/transgroups")
    @RateLimit(capacity = 60, limitType = LimitType.USER_ID)
    public ApiResponse<TransGroupOfUserResponse> getMyGroup() {
        return ApiResponse.<TransGroupOfUserResponse>builder()
                .result(transGroupService.getMyGroup())
                .build();
    }


    // ----------------------------------- endpoint public (cho khách) -----------------------------------//

    // Endpoint này dành cho user thông thường có thể thấy tất cả nhóm dịch đang hoạt động
    @RateLimit(capacity = 60)
    @GetMapping("/transgroups")
    public ApiResponse<PaginatedResponse<TransGroupSummaryResponse>> getGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        PaginatedResponse<TransGroupSummaryResponse> responses = transGroupService.getGroups(page, size, sortBy);

        return ApiResponse.<PaginatedResponse<TransGroupSummaryResponse>>builder()
                .result(responses)
                .build();
    }

    @GetMapping("/transgroups/{idOrSlug}")
    @RateLimit(capacity = 60, limitType = LimitType.USER_ID)
    public ApiResponse<TransGroupDetailsResponse> getGroupByIdOrSlug(@PathVariable @NonNull String idOrSlug) {

        TransGroupDetailsResponse response = transGroupService.getGroupByIdOrSlug(idOrSlug);

        return ApiResponse.<TransGroupDetailsResponse>builder()
                .result(response)
                .build();
    }


    // ----------------------------------- endpoint cho admin -----------------------------------//

    @GetMapping("/admin/transgroups")
    @RateLimit(capacity = 30, limitType = LimitType.USER_ID)
    public ApiResponse<PaginatedResponse<TransGroupSummaryResponse>> getGroups(
            @RequestParam(required = false) TransGroupStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        PaginatedResponse<TransGroupSummaryResponse> responses = transGroupService.getGroups(status, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<TransGroupSummaryResponse>>builder()
                .result(responses)
                .build();
    }


    // ----------------------------------- endpoint cho leader và admin -----------------------------//

    @DeleteMapping("/transgroups/{id}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<DeleteTransGroupResponse> softDeleteGroupById(@PathVariable @NonNull String id) {
        transGroupService.softDeleteGroupById(id);

        return ApiResponse.<DeleteTransGroupResponse>builder()
                .message(ResponseCode.SUCCESS.getMessage())
                .result(DeleteTransGroupResponse.builder().groupId(id).build())
                .build();
    }

    @PatchMapping("/transgroups/{id}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<TransGroupResponse> updateGroupById(
            @PathVariable @NonNull String id,
            @Valid @ModelAttribute("transGroupUpdateRequest") TransGroupUpdateRequest request)
    {
        TransGroupResponse response = transGroupService.updateGroupById(id, request);

        return ApiResponse.<TransGroupResponse>builder()
                .result(response)
                .build();
    }

}
