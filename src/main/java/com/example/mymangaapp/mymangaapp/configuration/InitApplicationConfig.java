package com.example.mymangaapp.mymangaapp.configuration;

import java.util.List;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.constant.SequenceCounterConstants;
import com.example.mymangaapp.mymangaapp.entity.*;
import com.example.mymangaapp.mymangaapp.repository.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitApplicationConfig {

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

            if (sequenceCounterRepository.count() == 0) {
                List<SequenceCounter> scs = List.of(
                        SequenceCounter.builder().name(SequenceCounterConstants.MANGA).nextValue(1L).build(),
                        SequenceCounter.builder().name(SequenceCounterConstants.TRANSGROUP).nextValue(1L).build()
                );
                sequenceCounterRepository.saveAll(scs);
            }

            log.info(".................Init application ends...................");
        };
    }

}
