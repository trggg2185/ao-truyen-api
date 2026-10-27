package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, String> {

    @EntityGraph(attributePaths = { "manga" })
    @NonNull
    Optional<Chapter> findById(@NonNull String id);

    /*
     * DTO-based Projection (SELECT new ...):
     * - Dùng khi user xem danh sách chương của 1 bộ truyện (GET /mangas/{mangaId}/chapters).
     * - Chỉ SELECT đúng 5 cột cần thiết (id, chapterIndex, title, views, updatedAt), bỏ qua
     *   việc khởi tạo Entity Chapter và không động tới danh sách pages.
     * - Lưu ý: truyền c.updatedAt khớp đúng với trường updatedAt của ChapterSummaryResponse.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse(
                c.id, c.chapterIndex, c.title, c.views, c.updatedAt
            )
            FROM Chapter c
            WHERE c.manga.id = :mangaId
    """)
    Page<ChapterSummaryResponse> findSummariesByMangaId(@Param("mangaId") String mangaId, Pageable pageable);

    // Check cùng 1 bộ truyện không thể có 2 chương trùng chapterIndex
    boolean existsByMangaIdAndChapterIndex(String mangaId, Integer chapterIndex);

}
