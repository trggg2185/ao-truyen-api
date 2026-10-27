package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.chapter.response.PageResponse;
import com.example.mymangaapp.mymangaapp.entity.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, String> {

    /*
     * DTO-based Projection cho danh sách trang ảnh của 1 chương (GET /chapters/{chapterId}/pages).
     * - Đây là endpoint được gọi nhiều nhất khi người đọc lật trang truyện.
     * - Một chương có thể tới 150 trang: dùng "SELECT new PageResponse(...)" chỉ lấy 3 cột
     *   (id, pageNumber, imageUrl) và tạo thẳng DTO, không nạp 150 Entity Page vào Persistence Context,
     *   cũng không cần dùng PageMapper để map lại trong Java.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.chapter.response.PageResponse(
                p.id, p.pageNumber, p.imageUrl
            )
            FROM Page p
            WHERE p.chapter.id = :chapterId
    """)
    List<PageResponse> findAllResponsesByChapterId(@Param("chapterId") String chapterId, Sort sort);

}
