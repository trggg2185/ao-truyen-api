package com.example.mymangaapp.mymangaapp.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PaginationUtils {

    // class paginatio utils giúp paginate an toàn hơn
    // desc (descending): giảm dần
    // asc (ascending): tăng dần

    private static final int MAX_PAGE_SIZE = 50; // tối đa 50 bản ghi 1 trang cho phân trang
    private static final String DEFAULT_SORT_BY = "createdAt"; // sort mặc định theo cột này
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("updatedAt", "chapterIndex"); // các cột được phép sort theo

    private PaginationUtils() {}

    public static Pageable createPageable(
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {

        int safePage = Math.max(page, 0); // page ko nhận số âm
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE); // nếu số lượng nhỏ hơn 1 thì lấy 1, lớn hơn max thì lấy max, giữa thì lấy chính nó
        String safeSort = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy))
                ? sortBy
                : DEFAULT_SORT_BY;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, safeSort));
    }

}
