package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransGroupResponse {

    // Response chi tiết của nhóm dịch (thay thế luôn cho TransGroupDetailsResponse cũ bị dư thừa).
    // Danh sách truyện của nhóm được tách sang endpoint phân trang riêng: GET /transgroups/{groupId}/mangas.

    String id;
    UserSummaryResponse leader;
    String name;
    String slug;
    String avatar;
    String coverImageUrl;
    TransGroupStatus status;
    Set<UserSummaryResponse> members;
    String description;
    Instant createdAt;

}
