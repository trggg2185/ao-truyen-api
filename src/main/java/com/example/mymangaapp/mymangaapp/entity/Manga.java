package com.example.mymangaapp.mymangaapp.entity;

import java.util.List;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.enums.MangaStatus;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Entity
@Table(
        name = "manga",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_manga_slug", columnNames = "slug") // Đặt tên cho constraint
        }
)
public class Manga extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, length = 200)
    String name;

    @Column(nullable = false, length = 300)
    String slug;

    String authorsName;

    @Column(length = 500)
    String coverImageUrl;

    // Sô thứ tự của truyện, được tạo sẵn lúc tạo truyện, kothể update
    @Column(nullable = false, updatable = false)
    Long sequenceNumber;

    // ko nên generate slug bằng prepersist và preupdate
    // ta sẽ xử lý trong service

    // Quan hệ với bảng thể loại, 1 manga có nhiều thể loại, 1 thể loại thuộc về nhiều manga
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "manga_categories",
        joinColumns = @JoinColumn(name = "manga_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    Set<Category> categories;

    // Lưu enum dưới dạng chuỗi (varchar) thay vì số 0, 1, 2
    @Enumerated(EnumType.STRING)
    // Set value mặc định vì dùng JPA tạo, cập nhật bảng
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(15) DEFAULT 'ONGOING'")
    // Để Lombok ko bỏ qua giá trị khởi tạo = MangaStatus.ONGOING khi dựng object bằng Builder
    @Builder.Default
    MangaStatus status = MangaStatus.ONGOING;

    @Column(name = "description", length = 500)
    String description;

    // QH: 1 manga có thể có nhiều trans group dịch
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "manga_transgroups",
        joinColumns = @JoinColumn(name = "manga_id"),
        inverseJoinColumns = @JoinColumn(name = "transgroup_id")
    ) // luôn luôn nên dùng Set đặc biệt do ManyToMany
    Set<TransGroup> transGroups;

    // QH: 1 manga có nhiều chapter
    // thêm orphanremoval = true giúp khi xoá manga sẽ đồng thời xoá các chapter của manga đó
    // bên chapter cũng có, khi xoá chapter thì page cũng xoá theo luôn
    @OneToMany(mappedBy = "manga", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterIndex ASC") // luôn lấy chapter tăng dần theo chapterIndex
    List<Chapter> chapters;

}
