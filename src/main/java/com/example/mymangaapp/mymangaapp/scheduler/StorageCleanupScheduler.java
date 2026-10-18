package com.example.mymangaapp.mymangaapp.scheduler;

import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StorageCleanupScheduler {

    ObjectStorage objectStorage;

    static String TMP_PREFIX = "tmp/";
    static Duration TMP_THRESHOLD = Duration.ofHours(3); // 3 tiếng


    // -----------------------------------chức năng của hệ thống----------------------------------- //

    // Cron expression: second - minute - hour - day of month - month - day of week
    // 2 số 0 ở đầu nghĩa là chạy vào lúc 0 giây 0 phút của mọi giờ, mọi ngày
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTmpFiles() {
        try {
            // lấy tg lúc 3 tiếng trc
            Instant thresholdTime = Instant.now().minus(TMP_THRESHOLD);

            // gọi hàm xoá bên storage service
            objectStorage.deleteFilesWithPrefix(TMP_PREFIX, thresholdTime);

        } catch (Exception e) {
            log.error("Lỗi dọn dẹp tmp r2!", e);
        }

    }

    // Chạy vào lúc 3h sáng mỗi ngày để dọn dẹp các files đã chuyển từ tmp sang mangas
    // nhưng bị lỗi trong qtrình copy
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupFailedUploadedFiles() {
        // Chưa làm được, làm sau
    }

}
