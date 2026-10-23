package com.example.mymangaapp.mymangaapp.security.social;

import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @AllArgsConstructor
@NoArgsConstructor
@Builder @FieldDefaults(level = AccessLevel.PRIVATE)
public class ExternalIdentity {

    AuthProvider provider;
    String providerUserId;
    String email;
    boolean emailVerified;
    String displayName;
    String avatarUrl;

}
