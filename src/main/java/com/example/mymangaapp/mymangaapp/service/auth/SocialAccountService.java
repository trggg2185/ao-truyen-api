package com.example.mymangaapp.mymangaapp.service.auth;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.entity.UserIdentity;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.UserIdentityRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.security.social.ExternalIdentity;
import com.example.mymangaapp.mymangaapp.utils.UsernameGenerator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocialAccountService {

    UserRepository userRepository;
    UserIdentityRepository identityRepository;
    RoleRepository roleRepository;

    @Value("${app.init.admin.email}")
    @NonFinal
    String adminEmail;

    @Transactional
    public String findOrCreateUserId(ExternalIdentity externalIdentity) {

        Optional<String> userIdOpt = identityRepository
                .findUserId(externalIdentity.getProvider(), externalIdentity.getProviderUserId());

        // Nếu có user thì xét
        if (userIdOpt.isPresent()) {

            // nếu user bị banned thì throw
            if (userRepository.isUserBannedById(userIdOpt.get()))
                throw new AppException(ResponseCode.USER_BANNED);
            else // ko thì trả về userId
                return userIdOpt.get();
        }

        // nếu ko có userid thì tạo user mới
        return createUser(externalIdentity);
    }

    private String createUser(ExternalIdentity externalIdentity) {

        String email = externalIdentity.isEmailVerified() ? externalIdentity.getEmail() : null;

        if (email == null) {
            throw new AppException(ResponseCode.EMAIL_REQUIRED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new AppException(ResponseCode.EMAIL_USED_BY_ANOTHER_LOGIN_METHOD);
        }

        String roleName = email.equals(adminEmail) ? RoleConstants.ADMIN : RoleConstants.USER;

        Role role = roleRepository.findById(roleName)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));

        String username = UsernameGenerator.fromEmail(email);
        User user = User.builder()
                .username(username)
                .email(email)
                .displayName(username) // đăng nhập bằng google thì set display name là username luôn (user có thể sửa)
                .avatar(externalIdentity.getAvatarUrl()) // lấy đg link avatar của google, ko tốn băng thông của mình
                .roles(new HashSet<>(Set.of(role)))
                .build();
        userRepository.save(user);

        identityRepository.save(UserIdentity.builder()
                .user(user)
                .provider(externalIdentity.getProvider())
                .providerUserId(externalIdentity.getProviderUserId())
                .email(externalIdentity.getEmail())
                .build());

        return user.getId();
    }

}
