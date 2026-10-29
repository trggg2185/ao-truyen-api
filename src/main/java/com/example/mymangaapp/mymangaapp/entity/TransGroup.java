package com.example.mymangaapp.mymangaapp.entity;

import java.util.Set;

import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder @Entity
@Table(
        name = "trans_group",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transgroup_name_sequence",
                        columnNames = { "name", "sequence_number" }
                )
        }
)
public class TransGroup extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    // định nghĩa lại là 1 user có thể là leader nhiều nhóm
    // nhiều nhóm ở đầy là cả nhóm đang hoạt động và nhóm đã xoá
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    User leader;

    // QH: 1 trans group có thể có nhiều thành viên
    // Báo cho JPA biết quan hệ này đã được quản lý bởi biến nào ở entity kia.
    // Bên có mappedBy sẽ KHÔNG tự sinh ra khóa ngoại.
    // Mặc định OneToMany đã có fetch lazy rồi nhưng thêm vào để cho rõ nghĩa
    @OneToMany(mappedBy = "transGroup", fetch = FetchType.LAZY)
    Set<User> members;

    // bỏ unique, ko đc null
    @Column(length = 50, nullable = false)
    String name;

    // bảng transgroup bổ sung thêm trường slug
    @Column(length = 100, nullable = false, unique = true)
    String slug;

    @Column(length = 500)
    String avatar;

    @Column(length = 500)
    String coverImageUrl;

    // Sô thứ tự của truyện, được tạo sẵn lúc tạo truyện, kothể update
    @Column(nullable = false, updatable = false)
    Long sequenceNumber;

    @Column(length = 300)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(15) DEFAULT 'APPROVED'")
    @Builder.Default
    TransGroupStatus status = TransGroupStatus.APPROVED;

    // QH: 1 trans group có thể dịch nhiều manga
    // đây chỉ là ds manga mà nhóm dịch, chưa chắc đã sở hữu (tạo ra), có thể chỉ là dịch phụ
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "transGroups")
    Set<Manga> translatedMangas;

}
