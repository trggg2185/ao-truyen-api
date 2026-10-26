package com.example.mymangaapp.mymangaapp.service.storage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component @Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageCleanup {

    ObjectStorage objectStorage;
    ExecutorService storageExecutor;

    // Xoá các files trong prefix sau khi transaction commit, chạy nền ko chặn request
    public void deletePrefixAfterCommit(String prefix) {

        Assert.state(TransactionSynchronizationManager.isSynchronizationActive(),
                "deletePrefixAfterCommit phải được gọi trong @Transactional");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageExecutor.execute(() -> {
                    try {
                        objectStorage.deleteFilesWithPrefix(prefix, Instant.MAX);   // mọi file dưới prefix
                    } catch (Exception e) {
                        // chỉ để lại file thừa, không ảnh hưởng dữ liệu
                        log.error("Không xóa được file với prefix {}", prefix, e);
                    }
                });
            }
        });

    }

    // Dọn rác nếu transaction bị rollback
    public void registerDeleteOnRollback(List<String> keys) {
        // Kiểm tra xem luồng hiện tại có đang nằm trong 1 Transaction thật hay không
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;

                try {
                    objectStorage.deleteFiles(keys);
                    log.warn("Transaction không commit, đã dọn {} file trên R2", keys.size());
                } catch (Exception e) {
                    log.error("Không dọn được {} file rác trên R2, key đầu tiên {}", keys.size(), keys.getFirst(), e);
                }
            }
        });
    }

}
