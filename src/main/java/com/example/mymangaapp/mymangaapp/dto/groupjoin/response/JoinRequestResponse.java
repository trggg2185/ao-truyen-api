package com.example.mymangaapp.mymangaapp.dto.groupjoin.response;

import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.enums.GroupJoinRequestStatus;
import com.example.mymangaapp.mymangaapp.enums.UserStatus;
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
    GroupJoinRequestStatus status;
    Instant createdAt;
    Instant updatedAt;

    /*
     * Constructor "phẳng" phục vụ DTO-based Projection (GroupJoinRequestRepository.findResponses).
     *
     * Cách hoạt động:
     * - JPQL "SELECT new" không hỗ trợ khởi tạo lồng nhiều biểu thức "new" bên trong nhau
     *   trên mọi phiên bản Hibernate. Vì vậy, ta truyền các cột phẳng của 3 bảng
     *   (group_join_request, trans_group, user) vào constructor này, rồi tự dựng
     *   TransGroupSummaryResponse và UserSummaryResponse ở bên trong.
     * - Nhờ đó, chỉ tốn 1 câu SQL JOIN lấy đúng 13 cột cần thiết, thay vì dùng @EntityGraph
     *   kéo toàn bộ 3 Entity (hơn 30 cột) lên RAM rồi mới dùng MapStruct để map.
     */
    public JoinRequestResponse(
            String id,
            GroupJoinRequestStatus status,
            Instant createdAt,
            Instant updatedAt,
            String groupId,
            String groupName,
            String groupSlug,
            String groupAvatar,
            String userId,
            String username,
            String displayName,
            String userAvatar,
            UserStatus userStatus
    ) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.transGroup = new TransGroupSummaryResponse(groupId, groupName, groupSlug, groupAvatar);
        this.user = new UserSummaryResponse(userId, username, displayName, userAvatar, userStatus);
    }

}
