package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransGroupSummaryResponse {

    // response transgroup tối giản nhất, thường dùng để đi kèm khi lấy cả 1 ds

    String id;
    String name;
    String slug;
    String avatar;

}
