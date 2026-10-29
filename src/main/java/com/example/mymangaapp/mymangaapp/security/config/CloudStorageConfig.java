package com.example.mymangaapp.mymangaapp.security.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Cấu hình cho dịch vụ lưu trũ cloud r2 object storage của cloudflare
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudStorageConfig {

    @Value("${cloud.r2.credentials.access-key-id}")
    String accessKeyId;

    @Value("${cloud.r2.credentials.secret-access-key}")
    String secretAccessKey;

    @Value("${cloud.r2.s3.endpoint-url}")
    String endpointUrl;

    @Value("${cloud.r2.region.static}")
    String region;

    /*
    * - S3 client là main object trong aws sdk, để làm gateway tương tác với dịch vụ s3-compatible như r2 object storage
    * - Nó cung cấp các method như:
    *   + upload file
    *   + down file
    *   + xoá file
    *   + lấy file
    *   + tạo, xoá bucket
    * - Cơ chế của AWS SDK khi đang thực hiện thao tác mà mất mạng: Khi bạn dùng S3Client, AWS mặc định thiết lập 2 thứ rất "lì lợm":
    *   + Retry Policy (Chính sách thử lại): Nếu rớt mạng hoặc S3 phản hồi chậm, SDK sẽ không báo lỗi ngay.
    *     Nó sẽ ngầm tự động thử lại (retry) 3-5 lần, với thời gian chờ tăng dần (Exponential Backoff).
    *   + TCP Socket Timeout: Khi bạn ngắt mạng đột ngột, kết nối TCP không gửi tín hiệu "Đóng" (RST).
    *     Hệ điều hành và HTTP Client của AWS SDK sẽ rơi vào trạng thái "chờ đợi mù quáng" có thể lên tới 60 giây hoặc
    *     vài phút trước khi chính thức kết luận là "Mất mạng".
    * ==> Tác hại: Cơ chế này rất tốt cho các Job chạy ngầm, nhưng với REST API, nó sẽ block (khóa) Thread của Web Server.
    *              Nếu 100 người dùng cùng upload lúc rớt mạng, 100 Thread của Tomcat sẽ bị treo, dẫn đến sập toàn bộ hệ thống!
    * */
    @Bean
    S3Client s3Client() {
        // Đối tượng chứa thông tin xác thực
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId,
                secretAccessKey
        );

        // ghi đề cấu hình cho tất cả các requests gọi từ client này
        ClientOverrideConfiguration config = ClientOverrideConfiguration.builder()
                // tổng tg tối đa gọi api
                .apiCallTimeout(Duration.ofSeconds(20))
                // thời gian cho mỗi lần thử gọi api (5s kể từ khi gọi api mà có lỗi, vứt exception luôn)
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                // giảm số lần thử gọi api xuống còn 1
                .retryStrategy(StandardRetryStrategy.builder()
                        .maxAttempts(2)
                        .build())
                .build();

        return S3Client.builder()
                // ghi đè endpoint của aws thành r2, để sdk gửi đến đúng server r2
                .endpointOverride(URI.create(endpointUrl))
                // ghi đề cấu hình cho requests
                .overrideConfiguration(config)
                // khu vực
                .region(Region.of(region))
                // thông tin xác thực
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService storageExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
