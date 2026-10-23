package com.example.mymangaapp.mymangaapp.security.service;

import java.util.HashSet;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

// UserDetailsService quan trọng khi dùng jwt authentication
// Nhiệm vụ của nó là nhận username trả về User
// Mục đích là phục vụ cho OncePerRequestFilter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class CustomUserDetailsService implements UserDetailsService {

    UserRepository userRepository;

    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        User user = userRepository
                .findForAuthByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.UNAUTHENTICATED));

        // Ta sẽ nạp tất cả các roles và permissions mà user có vào đây
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (!CollectionUtils.isEmpty(user.getRoles())) {
            for (Role role : user.getRoles()) {
                // Nạp role nhưng phải có prefix ROLE_
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

                if (!CollectionUtils.isEmpty(role.getPermissions())) {
                    // Nạp permission, permission thì ko cần prefix
                    role.getPermissions().forEach(permission ->
                            authorities.add(new SimpleGrantedAuthority(permission.getName())));
                }
            }
        }

        return CustomUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .authorities(authorities)
                .build();
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserById(String userId) {
        User user = userRepository
                .findForAuthById(userId)
                .orElseThrow(() -> new AppException(ResponseCode.UNAUTHENTICATED));

        // Ta sẽ nạp tất cả các roles và permissions mà user có vào đây
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (!CollectionUtils.isEmpty(user.getRoles())) {
            for (Role role : user.getRoles()) {
                // Nạp role nhưng phải có prefix ROLE_
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

                if (!CollectionUtils.isEmpty(role.getPermissions())) {
                    // Nạp permission, permission thì ko cần prefix
                    role.getPermissions().forEach(permission ->
                            authorities.add(new SimpleGrantedAuthority(permission.getName())));
                }
            }
        }

        return CustomUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .authorities(authorities)
                .build();
    }

}
