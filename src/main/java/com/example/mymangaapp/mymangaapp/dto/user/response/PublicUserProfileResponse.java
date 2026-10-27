package com.example.mymangaapp.mymangaapp.dto.user.response;

import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.enums.UserStatus;
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
    UserStatus status;
    Instant createdAt;
    TransGroupSummaryResponse transGroup; // Group đang tham gia (member hoặc leader)

    /*
     * Constructor phục vụ DTO-based Projection (UserRepository.findPublicProfileById / ByUsername).
     * Câu query dùng LEFT JOIN từ User sang TransGroup (nhóm mà user làm member):
     * - Nếu user không thuộc nhóm nào (groupId == null) hoặc nhóm đã bị xoá (groupStatus != APPROVED),
     *   transGroup sẽ để null (sau đó Service sẽ kiểm tra tiếp xem user có đang làm leader nhóm nào không).
     * - Nếu user đang là member của nhóm APPROVED, dựng luôn TransGroupSummaryResponse ngay trong 1 câu query.
     */
    public PublicUserProfileResponse(
            String id,
            String username,
            String displayName,
            String avatar,
            String bio,
            String facebook,
            String discord,
            UserStatus status,
            Instant createdAt,
            String groupId,
            String groupName,
            String groupSlug,
            String groupAvatar,
            TransGroupStatus groupStatus
    ) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatar = avatar;
        this.bio = bio;
        this.facebook = facebook;
        this.discord = discord;
        this.status = status;
        this.createdAt = createdAt;
        if (groupId != null && groupStatus == TransGroupStatus.APPROVED) {
            this.transGroup = new TransGroupSummaryResponse(groupId, groupName, groupSlug, groupAvatar);
        }
    }

}
