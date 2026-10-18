package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MangaTransGroupService {

    MangaRepository mangaRepository;
    TransGroupRepository transGroupRepository;

    // Chỉ có thể tự mình rời dịch, ko được phép xoá nhóm khác khỏi dịch truyện
    // nhóm dịch này muốn rút, ko dịch truyện này nữa
    @Transactional
    @PreAuthorize("@groupSec.isGroupLeader(#groupId)") // chỉ leader mới có thể rời
    public void leave(@NonNull String mangaId, @NonNull String groupId) {

        // check có đúng là manga do nhóm này dịch ko
        if (!mangaRepository.isMangaTranslatedByGroup(mangaId, groupId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        Manga manga = mangaRepository
                .findWithTransGroupsById(mangaId)
                .orElseThrow(() -> new AppException(ResponseCode.MANGA_NOT_FOUND));

        // đếm số lượng nhóm dịch truyện này vẫn còn hoạt động (APPROVED)
        long numberApprovedGroups = manga.getTransGroups().stream()
                .filter(transGroup -> transGroup.getStatus().equals(TransGroupStatus.APPROVED))
                .count();
        // không cho phép rời khi còn mỗi nhóm đó dịch
        if (numberApprovedGroups <= 1) {
            throw new AppException(ResponseCode.MANGA_MUST_HAVE_GROUP);
        }

        TransGroup transGroup = transGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        // xoá nhóm đó khỏi dịch truyện này
        manga.getTransGroups().remove(transGroup);

        mangaRepository.save(manga);
    }

}
