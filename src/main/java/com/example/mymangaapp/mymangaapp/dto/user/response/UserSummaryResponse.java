package com.example.mymangaapp.mymangaapp.dto.user.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSummaryResponse {

    // response tối giản nhất có thể

    String id;
    String username;
    String displayName;
    String avatar;
    String status;
}
