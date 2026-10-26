package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.UserIdentity;
import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, String> {

    @Query("SELECT i.user.id  FROM UserIdentity i WHERE i.provider = :provider AND i.providerUserId = :sub")
    Optional<String> findUserId(@Param("provider") AuthProvider provider, @Param("sub") String sub);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByUserIdAndProvider(String userId, AuthProvider provider);

    // Xoá tất cả danh tính người dùng bằng user id
    @Modifying
    @Query("DELETE FROM UserIdentity i WHERE i.user.id = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

}