package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.dto.groupcreation.request.CreationRequest;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.groupcreation.response.CreationRequestResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupResponse;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import com.example.mymangaapp.mymangaapp.service.GroupCreationRequestService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupCreationRequestController {

    GroupCreationRequestService groupCreationRequestService;


    // --------------------------chức năngdành cho user dãđăng nhập----------------------------------

    @PostMapping("/creation-requests")
    public ApiResponse<CreationRequestResponse> requestCreateGroup(@Valid @RequestBody CreationRequest request) {

        CreationRequestResponse response = groupCreationRequestService.requestCreateGroup(request);

        return ApiResponse.<CreationRequestResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/users/me/creation-requests")
    public ApiResponse<PaginatedResponse<CreationRequestResponse>> getMyCreationRequests(
            @RequestParam(required = false) GroupCreationRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        PaginatedResponse<CreationRequestResponse> response = groupCreationRequestService
                .getMyCreationRequests(status, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<CreationRequestResponse>>builder()
                .result(response)
                .build();
    }


    // ----------------------------chức năng dành cho admin ---------------------------------

    @PatchMapping("/admin/creation-requests/{requestId}/approve")
    public ApiResponse<TransGroupResponse> approveCreateGroup(@PathVariable @NonNull String requestId) {

        TransGroupResponse response = groupCreationRequestService.approveCreateGroup(requestId);

        return ApiResponse.<TransGroupResponse>builder()
                .result(response)
                .build();
    }

    @PatchMapping("/admin/creation-requests/{requestId}/reject")
    public ApiResponse<CreationRequestResponse> rejectCreateGroup(@PathVariable @NonNull String requestId) {

        CreationRequestResponse response = groupCreationRequestService.rejectCreateGroup(requestId);

        return ApiResponse.<CreationRequestResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/admin/creation-requests")
    public ApiResponse<PaginatedResponse<CreationRequestResponse>> getAllCreationRequests(
            @RequestParam(required = false) GroupCreationRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {

        PaginatedResponse<CreationRequestResponse> response = groupCreationRequestService
                .getAllCreationRequests(status, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<CreationRequestResponse>>builder()
                .result(response)
                .build();
    }

}
