package com.example.mymangaapp.mymangaapp.dto.transgroup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TransferLeadershipRequest {

    @NotBlank(message = "NEW_LEADER_REQUIRED")
    String newLeaderId;

}
