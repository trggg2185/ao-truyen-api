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

    @EntityGraph(attributePaths = { "pages" })
    Optional<Chapter> findWithPagesById(String id);

    @EntityGraph(attributePaths = { "manga" })
    @NonNull
    Optional<Chapter> findById(@NonNull String id);

    Page<Chapter> findByMangaId(String mangaId, Pageable pageable);

    // Query để lấy ds tối giản của chapter
    // dùng user xem chi tiết truyện có kèmtheo ds chapter thay vì EntityGraph ở bên manga
    @Query("""

            SELECT new com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse(c.id, c.chapterIndex, c.title, c.views, c.createdAt)
            FROM Chapter c
            WHERE c.manga.id = :mangaId
    """)
    Page<ChapterSummaryResponse> findSummariesByMangaId(@Param("mangaId") String mangaId, Pageable pageable);

    // Check cùng 1 chapter không thể có 2 chapter index giống nhau
    boolean existsByMangaIdAndChapterIndex(String mangaId, Integer chapterIndex);
}
