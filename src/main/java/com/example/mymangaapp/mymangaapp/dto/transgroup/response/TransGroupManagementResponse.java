package com.example.mymangaapp.mymangaapp.dto.transgroup.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransGroupManagementResponse {

    // transgroup response dành cho manga management response
    String id;
    String name;
    String slug;
    Boolean canRemove; // nhóm dịch này có được phép xoá không

}
