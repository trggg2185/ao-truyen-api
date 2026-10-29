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
            log.info(".....................Init application starts....................");

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

            if (roleRepository.count() == 0) {
                roleRepository.save(Role.builder()
                        .name("ADMIN")
                        .description("Role admin")
                        .permissions(Set.of(createPost, createManga, updateManga))
                        .build());

                roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Role user")
                        .permissions(Set.of(createPost))
                        .build());

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
                        Category.builder().name("Drama").description("Đau tim").build(),
                        Category.builder().name("Adult").description("Nội dung người lớn").build(),
                        Category.builder().name("Adventure").description("Phiêu lưu").build(),
                        Category.builder().name("Ecchi").description("Ecchi").build(),
                        Category.builder().name("Harem").description("Hậu cung").build(),
                        Category.builder().name("Historical").description("Lịch sử").build(),
                        Category.builder().name("Horror").description("Kinh dị").build(),
                        Category.builder().name("Isekai").description("Chuyển sinh").build(),
                        Category.builder().name("Mecha").description("Mecha").build(),
                        Category.builder().name("Mystery").description("Huyền bí").build(),
                        Category.builder().name("Oneshot").description("1 bắn").build(),
                        Category.builder().name("Psychological").description("Tâm lý").build(),
                        Category.builder().name("School Life").description("Cuộc sống học đường").build(),
                        Category.builder().name("Slice Of Life").description("Đời thường").build(),
                        Category.builder().name("Sci-Fi").description("Khoa học viễn tưởng").build(),
                        Category.builder().name("Seinen").description("Dành cho trung niên").build(),
                        Category.builder().name("Shoujo").description("Dành cho thiếu nữ").build(),
                        Category.builder().name("Shounen").description("Dành cho thiếu niên").build(),
                        Category.builder().name("Sport").description("Thể thao").build(),
                        Category.builder().name("Supernatural").description("Siêu năng lực").build(),
                        Category.builder().name("Yaoi").description("Tình cảm giữa các nhân vật nam").build(),
                        Category.builder().name("Yuri").description("Tình cảm giữa các nhân vật nữ").build(),
                        Category.builder().name("Thriller").description("Kịch tính").build(),
                        Category.builder().name("Tragedy").description("Bi kịch").build(),
                        Category.builder().name("Full Color").description("Truyện màu").build(),
                        Category.builder().name("Doujinshi").description("Truyện do cá nhân hoặc tác giả không chuyên sang tác").build()
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

            log.info(".........................Init application ends.......................");
        };
    }

}
