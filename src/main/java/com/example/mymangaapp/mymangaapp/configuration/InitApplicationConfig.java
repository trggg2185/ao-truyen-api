package com.example.mymangaapp.mymangaapp.configuration;

import java.util.List;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.constant.SequenceCounterConstants;
import com.example.mymangaapp.mymangaapp.entity.*;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.CategoryRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.mymangaapp.mymangaapp.repository.PermissionRepository;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitApplicationConfig {

    final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin.password}")
    String adminPassword;

    @Value("${app.init.user1.password}")
    String user1Password;

    @Value("${app.init.user2.password}")
    String user2Password;

    @Value("${app.init.user3.password}")
    String user3Password;

    @Bean
    ApplicationRunner applicationRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            CategoryRepository categoryRepository,
            TransGroupRepository transGroupRepository,
            SequenceCounterRepository sequenceCounterRepository
    ) {
        return args -> {
            log.info("................Init application starts...............");

            Permission createPost = permissionRepository
                    .findById("CREATE_POST")
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name("CREATE_POST")
                            .description("create new post permission")
                            .build()));

            Permission createManga = permissionRepository
                    .findById("CREATE_MANGA")
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name("CREATE_MANGA")
                            .description("create new manga permission")
                            .build()));

            Permission updateManga = permissionRepository
                    .findById("UPDATE_MANGA")
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name("UPDATE_MANGA")
                            .description("update manga permission")
                            .build()));

            // Role admin có full quyền
            Role adminRole = roleRepository
                    .findById("ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("ADMIN")
                            .description("Role admin")
                            .permissions(Set.of(createPost, createManga, updateManga))
                            .build()));

            Role userRole = roleRepository
                    .findById("USER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("USER")
                            .description("Role user")
                            .permissions(Set.of(createPost))
                            .build()));

            if (!roleRepository.existsById("TRANSLATOR")) {
                roleRepository.save(Role.builder()
                        .name("TRANSLATOR")
                        .description("Role translator")
                        .permissions(Set.of(createManga, updateManga))
                        .build());
            }

            if (categoryRepository.count() == 0) {
                List<Category> categories = List.of(
                        Category.builder().name("Action").description("Hành động").build(),
                        Category.builder().name("Fantasy").description("Huyễn tưởng").build(),
                        Category.builder().name("Romance").description("Lãng mạn").build(),
                        Category.builder().name("Comedy").description("Hài hước").build(),
                        Category.builder().name("Drama").description("Drama đau tim").build()
                );
                categoryRepository.saveAll(categories);
                log.info("Tạo các thể loại truyện mặc định thành công!");
            }

            // Khởi tạo 1 admin và 3 user
            if (userRepository.count() == 0) {
                User admin = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode(adminPassword))
                        .email("admin@gmail.com")
                        .roles(Set.of(adminRole))
                        .build();

                User user1 = User.builder()
                        .username("trg25")
                        .password(passwordEncoder.encode(user1Password))
                        .email("trg25@gmail.com")
                        .roles(Set.of(userRole))
                        .build();

                User user2 = User.builder()
                        .username("khoa12")
                        .password(passwordEncoder.encode(user2Password))
                        .email("khoa12@gmail.com")
                        .roles(Set.of(userRole))
                        .build();

                User user3 = User.builder()
                        .username("linh25")
                        .password(passwordEncoder.encode(user3Password))
                        .email("linh25@gmail.com")
                        .roles(Set.of(userRole))
                        .build();

                userRepository.saveAll(List.of(admin, user1, user2, user3));
                log.info("Tạo các user mặc định thành công!");
            }

            if (transGroupRepository.count() == 0) {
                User leader = userRepository
                        .findByUsername("admin")
                        .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

                TransGroup transGroup = TransGroup.builder()
                        .name("Admin Team")
                        .leader(leader)
                        // bỏ set leader vào trong mảng members
                        .status(TransGroupStatus.APPROVED)
                        .description("Transgroup của admin!")
                        .build();

                transGroupRepository.save(transGroup);
                log.info("Tạo nhóm dịch của admin thành công!");
            }

            List<SequenceCounter> scs = List.of(
                    SequenceCounter.builder().name(SequenceCounterConstants.MANGA).nextValue(1L).build(),
                    SequenceCounter.builder().name(SequenceCounterConstants.TRANSGROUP).nextValue(1L).build()
            );
            sequenceCounterRepository.saveAll(scs);

            log.info(".................Init application ends...................");
        };
    }

}
