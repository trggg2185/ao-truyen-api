package com.example.mymangaapp.mymangaapp.repository.projection;

/**
 * Interface-based Projection (Closed Projection) dùng để nạp hàng loạt (batch) danh sách Category
 * của nhiều bộ Manga chỉ bằng 1 câu truy vấn duy nhất (tránh lỗi N+1 query và Cartesian Product).
 *
 * CÁCH HOẠT ĐỘNG CỦA INTERFACE-BASED PROJECTION TRONG SPRING DATA JPA:
 * 1. Bạn chỉ cần khai báo một interface với các hàm getter (getXxx()). KHÔNG cần viết class cài đặt.
 * 2. Trong câu @Query JPQL, bạn đặt alias (AS) trùng khớp với tên thuộc tính sau chữ "get":
 *      - m.id          AS mangaId     -> khớp với getMangaId()
 *      - c.id          AS categoryId  -> khớp với getCategoryId()
 *      - c.name        AS name        -> khớp với getName()
 *      - c.description AS description -> khớp với getDescription()
 * 3. Khi thực thi, Hibernate chỉ SELECT đúng 4 cột này (không load Entity Manga hay Category).
 *    Spring Data JPA tự động sinh ra một đối tượng Proxy (JDK Dynamic Proxy) bọc lấy mảng kết quả
 *    (Tuple/Object[]) để khi bạn gọi getMangaId(), Proxy trả về đúng giá trị cột tương ứng.
 *
 * KHI NÀO DÙNG INTERFACE-BASED PROJECTION THAY VÌ DTO-BASED PROJECTION (SELECT new ...)?
 * - Khi dữ liệu trả về từ DB là dữ liệu trung gian nội bộ (ví dụ: cần kèm thêm khoá ngoại mangaId
 *   để gom nhóm `Collectors.groupingBy(MangaCategoryProjection::getMangaId)` ở tầng Service),
 *   chứ không trả thẳng ra JSON cho client. Dùng interface giúp không phải tạo thêm class DTO phụ.
 */
public interface MangaCategoryProjection {

    String getMangaId();

    Long getCategoryId();

    String getName();

    String getDescription();

}
