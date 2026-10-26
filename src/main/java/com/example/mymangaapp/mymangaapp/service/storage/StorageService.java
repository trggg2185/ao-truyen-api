package com.example.mymangaapp.mymangaapp.service.storage;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

// Giao tiếp với aws sdk để cung cấp các phương thức
// tương tác với file: lưu trữ, xoá, ...
// cho controller và các services khác dùng
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageService {

    ObjectStorage storage;
    ImageProcessor imageProcessor;
    ExecutorService storageExecutor;   // tên field trùng tên bean bên dưới
    StorageCleanup storageCleanup;

    @PreAuthorize("hasAnyRole('ADMIN', 'TRANSLATOR')")
    public String uploadTmpFile(@NonNull MultipartFile file) {
        return doUploadTmp(file);
    }

    /*
    * parallelStream() thay bằng pool riêng
    * parallelStream dùng chung ForkJoinPool.commonPool() của cả JVM, chỉ có (số core - 1) luồng.
    * Upload là việc chờ mạng, nên vài request upload chương truyện cùng lúc có thể chiếm hết luồng và làm các parallelStream
    * khác trong app đứng chờ. Pool riêng 4 luồng giới hạn được mức song song,
    * đồng thời giới hạn RAM: mỗi luồng giữ một ảnh đang giải nén, nên 40 trang không bao giờ có 40 ảnh nằm trong RAM cùng lúc.
    * Khi một file lỗi, join() ném CompletionException bọc ngoài, nên code phải bóc ra AppException để client nhận đúng mã lỗi.
    * */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRANSLATOR')")
    public List<String> uploadMultiTmpFiles(@NonNull List<MultipartFile> files) {
        List<CompletableFuture<String>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> doUploadTmp(file), storageExecutor))
                .toList();

        try {
            // join theo đúng thứ tự file gửi lên, quan trọng với thứ tự trang truyện
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException e) {
            futures.forEach(f -> f.cancel(true));   // task chưa chạy sẽ bị bỏ
            if (e.getCause() instanceof AppException appException) throw appException;
            throw e;
        }
    }


    public String uploadAvatar(String userId, MultipartFile file) {
        try {
            byte[] bytes = imageProcessor.toAvatar(file);
            return put(StorageKeysUtils.avatar(userId), bytes);
        } catch (IOException | SdkException e) {
            log.error("Upload avatar cho user thất bại, userId={}", userId, e);
            throw new AppException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    public String uploadGroupAvatar(String groupId, MultipartFile file) {
        try {
            byte[] bytes = imageProcessor.toAvatar(file);
            return put(StorageKeysUtils.groupAvatar(groupId), bytes);
        } catch (IOException | SdkException e) {
            log.error("Upload avatar cho nhóm dịch thất bại, groupId={}", groupId, e);
            throw new AppException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    public String uploadGroupCover(String groupId, MultipartFile file) {
        try {
            byte[] bytes = imageProcessor.toGroupCover(file);
            return put(StorageKeysUtils.groupCover(groupId), bytes);
        } catch (IOException | SdkException e) {
            log.error("Upload ảnh bìa cho nhóm dịch thất bại, groupId={}", groupId, e);
            throw new AppException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * Chuyển 1 file duy nhất từ tmp/ sang vị trí chính thức (Dùng cho Ảnh Cover Manga, Avatar User...)
     */
    public String promoteTmpFile(String tmpUrl, String destKey) {
        List<String> publicUrls = promoteTmpFiles(List.of(tmpUrl), List.of(destKey));
        return publicUrls.getFirst();
    }

    /**
     * Chuyển danh sách file từ tmp/ sang vị trí chính thức (Dùng cho các trang Ảnh Chapter)
     */
    public List<String> promoteTmpFiles(List<String> tmpUrls, List<String> destKeys) {
        if (tmpUrls.size() != destKeys.size()) {
            throw new IllegalArgumentException("Số lượng tmpUrls và destKeys không khớp nhau");
        }

        // 1. Validate & Resolve tmp keys
        List<String> tmpKeys = resolveTmpKeys(tmpUrls);

        // 2. Đăng ký dọn rác trên R2 nếu Transaction rollback
        storageCleanup.registerDeleteOnRollback(destKeys);

        // 3. Tiến hành copy file
        try {
            for (int i = 0; i < tmpKeys.size(); i++) {
                storage.copyFile(tmpKeys.get(i), destKeys.get(i), false);
            }
        } catch (NoSuchKeyException e) {
            log.warn("File tmp không còn tồn tại (có thể đã bị dọn): {}", e.getMessage());
            throw new AppException(ResponseCode.FILE_INVALID);
        } catch (SdkClientException e) {
            log.error("Không kết nối được R2 khi copy file", e);
            throw new AppException(ResponseCode.STORAGE_SERVICE_UNAVAILABLE);
        } catch (S3Exception e) {
            log.error("R2 trả lỗi khi copy file", e);
            throw new AppException(ResponseCode.STORAGE_SERVICE_ERROR);
        }

        // 4. Trả về public URLs
        return destKeys.stream().map(storage::toPublicUrl).toList();
    }

    // -------------------------------------- private methods --------------------------------------

    private String doUploadTmp(MultipartFile file) {
        try {
            byte[] bytes = imageProcessor.optimize(file);
            return put(StorageKeysUtils.tmp(), bytes);
        } catch (IOException | SdkException e) {
            log.error("Upload file tmp thất bại: {}", file.getOriginalFilename(), e);
            throw new AppException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    private String put(String key, byte[] bytes) {
        storage.put(key, bytes, ImageProcessor.OUTPUT_CONTENT_TYPE);
        log.info("Upload ảnh thành công!");
        return storage.toPublicUrl(key);
    }

    // Url -> key, và chỉ chấp nhận file nằm trong tmp/
    private List<String> resolveTmpKeys(List<String> tmpUrls) {
        return tmpUrls.stream()
                .map(url -> {
                    String key = storage.toKey(url);
                    if (!StorageKeysUtils.isTmp(key)) {
                        throw new AppException(ResponseCode.FILE_INVALID);
                    }
                    return key;
                })
                .toList();
    }

}
