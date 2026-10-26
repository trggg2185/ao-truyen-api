package com.example.mymangaapp.mymangaapp.dto.user.response;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CurrentUserResponse {

    // Đây là response cho trả về khi user lấy thông tin của mình
    // bằng cách truy cập vào profiles

    String id;
    String username;
    String displayName;
    String email;
    String facebook;
    String discord;
    String status;
    String bio;
    String avatar;
    Instant createdAt; // ngày tham gia (ngày tạo tk)
}
