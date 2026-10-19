package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

import com.example.mymangaapp.mymangaapp.enums.MyGroupStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransGroupOfUserResponse {

    // response trả về khi lấy nhóm dịch của user hiện tại

    TransGroupSummaryResponse transGroup; // null Khi status: PENDING_REQUEST / NONE
    MyGroupStatus status;

}
