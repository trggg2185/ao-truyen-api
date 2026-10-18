package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

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
public class TransGroupResponse {

    // response transgroup để xem chi tiết thì ổn hơn

    String id;
    UserSummaryResponse leader;
    String name;
    String slug;
    String avatar;
    String coverImageUrl;
    String status;
    Set<UserSummaryResponse> members;
    String description;
    Instant createdAt;

}
