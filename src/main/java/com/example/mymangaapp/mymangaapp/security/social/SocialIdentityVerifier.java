package com.example.mymangaapp.mymangaapp.security.social;

import com.example.mymangaapp.mymangaapp.enums.AuthProvider;

public interface SocialIdentityVerifier {

    AuthProvider provider();

    ExternalIdentity verify(String credential);

}
