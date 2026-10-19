package com.example.mymangaapp.mymangaapp.service.storage;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
// class này chỉ chuyên giao tiếp với r2
public class ObjectStorage {

    private static final int MAX_DELETE_BATCH = 1000;   // giới hạn của API xóa hàng loạt

    private final S3Client s3Client;

    @Value("${cloud.r2.bucket}")
    private String bucket;

    @Value("${cloud.r2.s3.public-url}")
    private String publicUrl;   // không có dấu "/" ở cuối

    public void put(String key, byte[] data, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(data));
    }

    public void copyFile(String sourceKey, String destinationKey, boolean deleteSource) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(sourceKey)
                .destinationBucket(bucket).destinationKey(destinationKey)
                .build());
        log.info("Copy file thành công");

        if (deleteSource) deleteFile(sourceKey);
        //return toPublicUrl(destinationKey);
    }

    public void deleteFile(String key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
        log.info("Đã xoá file");
    }

    /** Xóa các file dưới prefix có lastModified trước threshold. Trả về số file xóa thành công. */
    public void deleteFilesWithPrefix(String prefix, Instant threshold) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket).prefix(prefix).build();

        List<ObjectIdentifier> batch = new ArrayList<>(MAX_DELETE_BATCH);
        int deleted = 0;

        for (S3Object object : s3Client.listObjectsV2Paginator(request).contents()) {
            if (!object.lastModified().isBefore(threshold)) continue;

            batch.add(ObjectIdentifier.builder().key(object.key()).build());

            if (batch.size() == MAX_DELETE_BATCH) {
                deleted += deleteBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) deleted += deleteBatch(batch);

        log.info("Đã xoá {} files với prefix", deleted);
    }

    public void deleteFiles(Collection<String> keys) {
        List<ObjectIdentifier> ids = keys.stream()
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .toList();

        for (int i = 0; i < ids.size(); i += MAX_DELETE_BATCH) {
            deleteBatch(ids.subList(i, Math.min(i + MAX_DELETE_BATCH, ids.size())));
        }
    }

    private int deleteBatch(List<ObjectIdentifier> keys) {
        DeleteObjectsResponse response = s3Client.deleteObjects(
                b -> b.bucket(bucket).delete(d -> d.objects(keys)));

        // Xóa hàng loạt có thể thành công một phần, phải kiểm tra danh sách lỗi
        if (response.hasErrors() && !response.errors().isEmpty()) {
            response.errors().forEach(e ->
                    log.warn("Không xoá được {}: {}", e.key(), e.message()));
        }
        return keys.size() - response.errors().size();
    }

    public String toPublicUrl(String key) {
        return publicUrl + "/" + key;
    }

    /** Url công khai -> object key. Chỉ nhận url thuộc CDN của mình. */
    public String toKey(String url) {
        String prefix = publicUrl + "/";
        if (url == null || !url.startsWith(prefix)) {
            throw new AppException(ResponseCode.URL_INVALID);
        }
        return url.substring(prefix.length());
    }

}
