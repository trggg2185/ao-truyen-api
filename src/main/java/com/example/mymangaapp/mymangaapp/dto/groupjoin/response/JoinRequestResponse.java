package com.example.mymangaapp.mymangaapp.dto.groupjoin.response;

import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JoinRequestResponse {

    String id;
    TransGroupSummaryResponse transGroup;
    UserSummaryResponse user;
    String status;
    Instant createdAt;
    Instant updatedAt;

}
