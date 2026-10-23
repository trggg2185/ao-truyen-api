package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.dto.user.response.*;
import com.example.mymangaapp.mymangaapp.enums.LimitType;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import com.example.mymangaapp.mymangaapp.dto.user.request.UserUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.service.UserService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    // Nếu dùng validate ở trong controller như @NotBlank kết hợp với @Valid thì sẽ kích hoạt HandlerMethodValidator
    // ném ra HandlerMethodValidationException chứ không phải MethodArgumentNotValidException
    // nên muốn tắt id null type safety thì dùng @NonNull của spring để tắt warning 
    // chứ không nên dùng @NotBlank của jakarta

    UserService userService;


    // ---------------------- endpoint public đây (dành cho khách) -------------------------- //

    @GetMapping("/users/{idOrUsername}")
    public ApiResponse<PublicUserProfileResponse> getUserProfile(@PathVariable @NonNull String idOrUsername) {

        return ApiResponse.<PublicUserProfileResponse>builder()
                .result(userService.getUserProfile(idOrUsername))
                .build();
    }


    // --------------------------------- endpoint cho user đã đăng nhập -----------------------//

    @GetMapping("/users/me")
    @RateLimit(capacity = 60, limitType = LimitType.USER_ID)
    public ApiResponse<CurrentUserResponse> getMyInfo() {

        CurrentUserResponse response = userService.getMyInfo();

        return ApiResponse.<CurrentUserResponse>builder()
                .result(response)
                .build();

    }

    // chỉ định endpoint nhận multipart/form-data
    // @ModelAttribute sẽ bind các field trong form-data vào các field trong request object
    // khi đó trong postman ta làm việc bên form-data chứ ko bên raw json nữa
    @PatchMapping(value = "/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<CurrentUserResponse> updateMyInfo(
            @Valid @ModelAttribute("userUpdateRequest") UserUpdateRequest request

    ) {

        CurrentUserResponse response = userService.updateMyInfo(request);

        return ApiResponse.<CurrentUserResponse>builder()
                .result(response)
                .build();

    }


    // ---------------------------- endpoints cho admin --------------------------------- //

    @GetMapping("/admin/users")
    @RateLimit(capacity = 30, limitType = LimitType.USER_ID)
    public ApiResponse<PaginatedResponse<UserSummaryResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {

        PaginatedResponse<UserSummaryResponse> responses = userService.getAllUsers(page, size, sortBy);

        return ApiResponse.<PaginatedResponse<UserSummaryResponse>>builder()
                .result(responses)
                .build();

    }

    // fix lại chỉ có admin lấy đc user băng id
    @GetMapping("/admin/users/{id}")
    @RateLimit(capacity = 30, limitType = LimitType.USER_ID)
    public ApiResponse<UserResponse> getUserById(@PathVariable @NonNull String id) {

        UserResponse response = userService.getUserById(id);

        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();

    }

    // fix lại chỉ có adminmới đc update user
    @PatchMapping(value = "/admin/users/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<UserResponse> updateUserById(@PathVariable @NonNull String id,
            @Valid @ModelAttribute("userUpdateRequest") UserUpdateRequest request) {

        UserResponse response = userService.updateUserById(id, request);

        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();
    }


    // ---------------------------endpoint cho admin hoặc user --------------------------- //

    // xoá user bằng id
    @DeleteMapping("/admin/users/{id}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<DeleteUserResponse> deleteUserById(@PathVariable @NonNull String id) {

        userService.deleteUserById(id);

        return ApiResponse.<DeleteUserResponse>builder()
                .message(ResponseCode.SUCCESS.getMessage())
                .result(DeleteUserResponse.builder().userId(id).build())
                .build();
    }

}
