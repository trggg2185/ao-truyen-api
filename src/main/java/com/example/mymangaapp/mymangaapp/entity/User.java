package com.example.mymangaapp.mymangaapp.entity;

import java.util.Set;

import jakarta.persistence.*;
import lombok.experimental.SuperBuilder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@SuperBuilder
// khi bạn dùng @Builder trên class con (Manga), nó sẽ không nhận các field của class cha (createdAt).
// Để giải quyết, nếu bạn cần build cả field của cha, hãy đổi @Builder thành @SuperBuilder
// ở cả BaseEntity và Manga. (Tuy nhiên thực tế, ngày tạo và ngày sửa do DB tự động sinh
// ra nên ta hiếm khi phải nhét vào Builder)
public class User extends BaseEntity {

    /*
     * - Lazy fetch là mặc định trong các annotation sau: OneToMany, ManyToMany
     * - Lazy eager mặc định trong: ManyToOne, OneToOne
     * => nên lazy fetch hết cho tao
     * 
     * - Entity không nên validate mà nên validate ở request
     */

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    // username không phân biệt chữ hoa thường
    @Column(name = "username", nullable = false, unique = true, columnDefinition = "VARCHAR(50) COLLATE utf8mb4_unicode_ci")
    String username;

    @Column(name = "email", unique = true)
    String email;

    @Column(name = "password", nullable = false, length = 100)
    String password;

    String facebook;
    String discord;

    @Column(name = "bio", length = 300)
    String bio;

    @Column(length = 500)
    String avatar; // url của avatar

    // QH: Nhiều user có thể thuộc về 1 trans group
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transgroup_id") // chỉ định tên cột khoá ngoại sẽ sinh dưới db
    TransGroup transGroup;

    // QH: 1 user có thể có nhiểu role
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "users_id"),
            inverseJoinColumns = @JoinColumn(name = "roles_name")
    )
    Set<Role> roles;

    // 1userr làm leader nhiều nhóm (nhóm đang hoạtdđọng và nhóm đã xoá)
    @OneToMany(mappedBy = "leader", fetch = FetchType.LAZY)
    Set<TransGroup> transGroups;

}
