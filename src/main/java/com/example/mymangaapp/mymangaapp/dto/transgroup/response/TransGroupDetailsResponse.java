package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransGroupDetailsResponse {

    // info chi tiết của nhóm, khi user vào xem trang cá nhân của nhóm

    String id;
    UserSummaryResponse leader;
    String name;
    String slug;
    String coverImageUrl;
    String status;
    Set<MangaSummaryResponse> translatedMangas; // lấy tất truyện của nhóm đã dịch
    Set<UserSummaryResponse> members; // tất cả thành viên nhóm
    String description;
    Instant createdAt;

}
