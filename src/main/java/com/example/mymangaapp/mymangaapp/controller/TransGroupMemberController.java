package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransferLeadershipRequest;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.DeleteMemberResponse;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.service.TransGroupMemberService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransGroupMemberController {

    TransGroupMemberService transGroupMemberService;

    @DeleteMapping("/transgroups/{groupId}/users/{userId}")
    public ApiResponse<DeleteMemberResponse> kickMember(
            @PathVariable String groupId,
            @PathVariable String userId
    ) {

        transGroupMemberService.kickMember(groupId, userId);

        return ApiResponse.<DeleteMemberResponse>builder()
                .message(ResponseCode.SUCCESS.getMessage())
                .result(DeleteMemberResponse.builder().groupId(groupId).memberId(userId).build())
                .build();
    }

    @PatchMapping("/transgroups/{groupId}/leader")
    public ApiResponse<Void> transferLeaderShip(
            @PathVariable String groupId,
            @RequestBody @Valid TransferLeadershipRequest request
            ) {

        transGroupMemberService.transferLeadership(groupId, request);

        return ApiResponse.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .message(ResponseCode.SUCCESS.getMessage())
                .build();
    }

}
