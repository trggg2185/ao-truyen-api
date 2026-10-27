package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {

    @EntityGraph(attributePaths = { "permissions" })
    @NonNull
    Optional<Role> findById(@NonNull String id);

    /*
     * Dùng @EntityGraph nạp sẵn tập permissions trong 1 câu LEFT JOIN duy nhất cho getAllRoles().
     * Trước đây findAll() không có @EntityGraph nên khi RoleMapper đọc role.getPermissions()
     * bị dính lỗi N+1 query (1 câu lấy danh sách Role + N câu lấy Permission cho từng Role).
     * Lưu ý: Vì hàm trả về List<Role> (không phân trang LIMIT/OFFSET) và chỉ fetch 1 collection
     * nên dùng @EntityGraph ở đây hoàn toàn an toàn, không bị lỗi phân trang trên RAM (HHH90003004).
     */
    @EntityGraph(attributePaths = { "permissions" })
    @NonNull
    List<Role> findAll();

}
