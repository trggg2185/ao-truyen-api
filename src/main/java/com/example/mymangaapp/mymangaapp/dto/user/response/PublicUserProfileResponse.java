package com.example.mymangaapp.mymangaapp.dto.user.response;

import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PublicUserProfileResponse {

    // Đây là response trả về khi xem trang cá nhân của 1 user khác hoặc chính mình
    // nó khác với lại current user response là thông tin của tài khoản của user hiện tại

    String id;
    String username;
    String displayName;
    String avatar;
    String bio;
    String facebook;
    String discord;
    String status;
    Instant createdAt;
    TransGroupSummaryResponse transGroup; // Group đang tham gia

}
