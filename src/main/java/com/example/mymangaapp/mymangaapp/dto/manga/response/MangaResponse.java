package com.example.mymangaapp.mymangaapp.dto.manga.response;

import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MangaResponse {

    // Hợp nhất MangaResponse và MangaDetailsResponse thành 1 response duy nhất
    // cho cả danh sách trang chủ (GET /mangas), chi tiết truyện (GET /mangas/{idOrSlug}),
    // tạo truyện (POST /mangas) và cập nhật truyện (PATCH /mangas/{mangaId}).
    String id;
    String name;
    String slug;
    String coverImageUrl;
    String authorsName;
    // Dùng enum thay vì String để JPQL Constructor Expression ("SELECT new ...") truyền thẳng m.status vào.
    // Khi trả JSON cho client, Jackson vẫn tự động serialize enum thành chuỗi ("ONGOING", "COMPLETED",...).
    MangaStatus status;
    String description;
    Set<CategoryResponse> categories;
    List<TransGroupSummaryResponse> transGroups;
    Instant createdAt;
    Instant updatedAt;

    /*
     * Constructor dành riêng cho DTO-based Projection trong MangaRepository
     * (findPublicPage, findProjectedById, findProjectedBySlug).
     *
     * Cách hoạt động:
     * 1. JPQL "SELECT new ...MangaResponse(m.id, m.name, m.slug, m.coverImageUrl, m.authorsName,
     *    m.status, m.description, m.createdAt, m.updatedAt)" sẽ gọi trực tiếp constructor này.
     * 2. Hibernate chỉ SELECT đúng 9 cột vô hướng (scalar) này từ bảng manga và khởi tạo thẳng DTO,
     *    KHÔNG tạo đối tượng Entity Manga, KHÔNG đưa vào Persistence Context (1st-level cache),
     *    tiết kiệm bộ nhớ và bỏ qua chi phí dirty-checking.
     * 3. Các trường tập hợp (categories, transGroups) KHÔNG THỂ truyền vào constructor JPQL
     *    (vì SQL JOIN 1-N / N-N sẽ nhân bản số dòng và làm hỏng phân trang LIMIT/OFFSET).
     *    Do đó, Service sẽ nạp batch 2 collection này bằng Interface-based Projection rồi gán vào sau.
     */
    public MangaResponse(
            String id,
            String name,
            String slug,
            String coverImageUrl,
            String authorsName,
            MangaStatus status,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.coverImageUrl = coverImageUrl;
        this.authorsName = authorsName;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}
