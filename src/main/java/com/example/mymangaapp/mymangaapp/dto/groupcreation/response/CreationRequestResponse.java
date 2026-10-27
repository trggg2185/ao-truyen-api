package com.example.mymangaapp.mymangaapp.dto.groupcreation.response;

import lombok.*;
import com.example.mymangaapp.mymangaapp.enums.GroupCreationRequestStatus;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreationRequestResponse {

    String id;
    String groupName;
    String creatorName;
    GroupCreationRequestStatus status;
    String description;
    Instant updatedAt;

}
