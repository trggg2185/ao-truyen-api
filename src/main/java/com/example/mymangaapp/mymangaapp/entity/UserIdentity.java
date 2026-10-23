package com.example.mymangaapp.mymangaapp.entity;

import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder @Entity
@Table(
        name = "user_identity",
        uniqueConstraints = {
                // một tài khoản Google chỉ thuộc về một user
                @UniqueConstraint(name = "uk_identity_provider_subject", columnNames = { "provider", "provider_user_id" }),

                // một user chỉ liên kết một tài khoản cho mỗi nhà cung cấp
                @UniqueConstraint(name = "uk_identity_user_provider", columnNames = { "user_id", "provider" }),
        }
)
public class UserIdentity extends BaseEntity {

    // Vì user có nhiều loại giấy tờ nên không thể nhét trược tiếp vào làm
    // column trong bảng user, ta sẽ tạo bảng user identity (danh tính người dùng)

    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    AuthProvider provider;

    // "sub" của Google: định danh bất biến của tài khoản bên Google, dùng làm khóa liên kết
    @Column(nullable = false, length = 100)
    String providerUserId;

    // email nhà cung cấp trả về lúc liên kết, chỉ để tham khảo, KHÔNG dùng để định danh
    String email;
}
