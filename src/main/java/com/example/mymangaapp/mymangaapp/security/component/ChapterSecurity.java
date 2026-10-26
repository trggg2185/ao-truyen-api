package com.example.mymangaapp.mymangaapp.security.component;

import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component("chapterSec")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChapterSecurity {

    MangaRepository mangaRepository;

    public boolean canMofifyChapter(String mangaId) {

        // Nếu là admin có thể xoá chapter luôn
        if (SecurityUtils.isAdmin())
            return true;

        // Còn lại chỉ các thành viên trong nhóm dịch truyện này mới có quyền
        String currentUserId = SecurityUtils.getCurrentUserId();
        return mangaRepository.isMemberOrLeaderOfAnyGroupOfManga(currentUserId, mangaId, TransGroupStatus.APPROVED);
    }

}
