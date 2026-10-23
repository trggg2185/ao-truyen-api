package com.example.mymangaapp.mymangaapp.security.social;

import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocialIdentityVerifierRegistry {

    // Registry gom mọi verifier. Spring tự đưa vào đây tất cả bean cài đặt interface,
    // nên thêm nhà cung cấp mới không phải sửa registry

    Map<AuthProvider, SocialIdentityVerifier> verifiers;

    public SocialIdentityVerifierRegistry(List<SocialIdentityVerifier> verifierList) {
        this.verifiers = verifierList.stream()
                .collect(Collectors.toMap(SocialIdentityVerifier::provider, Function.identity()));
    }

    public SocialIdentityVerifier get(AuthProvider provider) {
        SocialIdentityVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new AppException(ResponseCode.AUTH_PROVIDER_NOT_SUPPORTED);
        }
        return verifier;
    }

}
