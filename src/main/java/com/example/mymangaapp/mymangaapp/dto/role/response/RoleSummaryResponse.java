package com.example.mymangaapp.mymangaapp.dto.role.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleSummaryResponse {

    String name;
    String description;

}
