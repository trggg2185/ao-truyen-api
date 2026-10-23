package com.example.mymangaapp.mymangaapp.security.social;

import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component @Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GoogleIdentityVerfier implements SocialIdentityVerifier {

    GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerfier(@Value("${app.auth.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public ExternalIdentity verify(String credential) {
        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new AppException(ResponseCode.SOCIAL_TOKEN_INVALID);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return ExternalIdentity.builder()
                    .provider(provider())
                    .providerUserId(payload.getSubject())
                    .email(payload.getEmail())
                    .avatarUrl((String) payload.get("picture"))
                    .emailVerified(Boolean.TRUE.equals(payload.getEmailVerified()))
                    .displayName((String) payload.get("name"))
                    .build();

        } catch (GeneralSecurityException | IOException e) {
            log.error("Lỗi không xác mình được Google ID token!", e);
            throw new AppException(ResponseCode.SOCIAL_SERVICE_UNAVAILABLE);
        }
    }
}
