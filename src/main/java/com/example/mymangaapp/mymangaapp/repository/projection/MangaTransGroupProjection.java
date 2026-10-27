package com.example.mymangaapp.mymangaapp.repository.projection;

import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;

/**
 * Interface-based Projection dùng để nạp hàng loạt (batch) danh sách nhóm dịch (TransGroup)
 * cho một hoặc nhiều bộ Manga trong 1 câu query duy nhất.
 *
 * Cách thức ánh xạ cột -> hàm getter thông qua alias trong JPQL:
 *   - m.id     AS mangaId -> getMangaId()
 *   - g.id     AS groupId -> getGroupId()
 *   - g.name   AS name    -> getName()
 *   - g.slug   AS slug    -> getSlug()
 *   - g.avatar AS avatar  -> getAvatar()
 *   - g.status AS status  -> getStatus()
 */
public interface MangaTransGroupProjection {

    String getMangaId();

    String getGroupId();

    String getName();

    String getSlug();

    String getAvatar();

    TransGroupStatus getStatus();

}
