package com.example.mymangaapp.mymangaapp.dto.user.response;

import java.time.Instant;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.dto.role.response.RoleSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
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
public class UserResponse {

    // response chi tiết của user, thường trả về cho admin

    String id;
    String username;
    String displayName;
    String email;
    String facebook;
    String discord;
    String avatar;
    String bio;
    String status;
    Set<RoleSummaryResponse> roles;
    TransGroupSummaryResponse transGroup;
    Instant createdAt;
    Instant updatedAt;
}
