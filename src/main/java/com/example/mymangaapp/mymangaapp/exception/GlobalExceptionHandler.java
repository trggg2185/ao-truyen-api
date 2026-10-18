package com.example.mymangaapp.mymangaapp.exception;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;

import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice // chuyển sang rest controller advice vì dự án dùng rest controller
@Slf4j
public class GlobalExceptionHandler {

    private static final String MIN_ATTRIBUTE = "min";
    private static final String MAX_ATTRIBUTE = "max";

    // Hanlde exception tổng quát, ngoài case đã được dự đoán
    @ExceptionHandler(Exception.class)
    private ResponseEntity<ApiResponse<?>> handlingGeneralException(Exception exception) {

        log.error("Lỗi cực kỳ nghiêm trọng sập hệ thống!", exception);

        ResponseCode responseCode = ResponseCode.UNCATEGORIZED_ERROR;

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle exception của app mà mình tự định nghĩa
    @ExceptionHandler(AppException.class)
    private ResponseEntity<ApiResponse<?>> handlingAppException(AppException exception) {

        log.error("Lỗi bắn ra ngoại lệ của ứng dụng!", exception);

        ResponseCode responseCode = exception.getResponseCode();

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle runtime exception
    @ExceptionHandler(RuntimeException.class)
    private ResponseEntity<ApiResponse<?>> handlingRuntimeException(RuntimeException exception) {

        log.error("Lỗi bắn ra ngoại lệ runtime!", exception);

        ResponseCode responseCode = ResponseCode.RUNTIME_EXCEPTION;

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle exception xảy ra khi vi phạm phân quyền ở service
    @ExceptionHandler(AuthorizationDeniedException.class)
    private ResponseEntity<ApiResponse<?>> handlingAuthorizationDeniedException(AuthorizationDeniedException exception) {

        log.warn("Authorization denied: {}", exception.getMessage());

        ResponseCode responseCode = ResponseCode.UNAUTHORIZED;

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle exception xảy ra khi upload file có kích thước quá lớn
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    private ResponseEntity<ApiResponse<?>> handlingMaxUploadSize(MaxUploadSizeExceededException exception) {

        log.warn("Upload rejected because the file is too large");

        ResponseCode responseCode = ResponseCode.FILE_SIZE_INVALID;

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle exception xảy ra khi duplicate key trong table db
    @ExceptionHandler(DataIntegrityViolationException.class)
    private ResponseEntity<ApiResponse<?>> handlingDataIntegrity(DataIntegrityViolationException exception) {

        log.warn("Data integrity violation: {}", exception.getMessage());

        ResponseCode responseCode = ResponseCode.DATA_INTEGRITY_VIOLATION;

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle exception xảy ra khi ko thể parse từ json trong body request sang object
    @ExceptionHandler(HttpMessageNotReadableException.class)
    private ResponseEntity<ApiResponse<?>> handlingHttpMessageNotReadable(HttpMessageNotReadableException exception) {

        log.warn("Malformed HTTP request: {}", exception.getMessage());

        // Mặc định bắn lỗi http message not readable
        ResponseCode responseCode = ResponseCode.HTTP_MESSAGE_NOT_READABLE;

        if (exception.getCause() instanceof InvalidFormatException invalidFormatException) {
            responseCode = ResponseCode.FIELD_VALUE_INVALID;

            String fieldName = invalidFormatException.getPath().getFirst().getFieldName();

            log.debug("Malformed request field: {}", fieldName);
        }

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Handle những exception bắn ra do vi phạm validate ở request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    private ResponseEntity<ApiResponse<?>> handlingValidationException(MethodArgumentNotValidException exception) {

        log.warn("Request validation failed: {}", exception.getMessage());

        ResponseCode responseCode = ResponseCode.ENUM_KEY_INVALID;

        FieldError fieldError = exception.getFieldError();

        // Lấy message ở field bị lỗi, chính là enum key của ResponseCode
        String enumKey = Objects.requireNonNull(fieldError).getDefaultMessage();

        try {
            responseCode = ResponseCode.valueOf(enumKey);
            // Bắt ngoại lệ enum key để tránh việc GlobalExceptionHandler đứng ra giải 
            // quyết và trả về response UNCATEGORIZED_ERROR 500
        } catch (IllegalArgumentException e) {
            log.error("Invalid response code key in validation metadata: {}", enumKey, e);
        }

        // Lấy đối tượng chứa thông tin chi tiết về 1 ràng buộc cụ thể đã vi phạm
        ConstraintViolation<?> constraintViolation = exception
                .getBindingResult()
                .getAllErrors()
                .getFirst()
                .unwrap(ConstraintViolation.class);

        // Đây là mảng thông tin vi phạm
        // ví dụ: nếu vi phạm min thì trong map sẽ có <"min", 3>
        Map<String, Object> attributes = constraintViolation.getConstraintDescriptor().getAttributes();

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(responseCode.getCode())
                .message(
                        Objects.nonNull(attributes)
                                ? mapAttribute(responseCode.getMessage(), attributes)
                                : responseCode.getMessage())
                .build();

        return ResponseEntity
                .status(responseCode.getHttpStatusCode())
                .body(apiResponse);
    }

    // Hàm chuyển message của response code thành 1 message cụ thể bằng
    // cách lấy thông tin từ ràng buộc bị vi phạm khi validate
    private String mapAttribute(String message, Map<String, Object> attributes) {

        if (message == null || attributes.isEmpty()) {
            log.warn("Response message or validation attributes are missing");
            return message;
        }

        String result = message;

        if (!Objects.isNull(attributes.get(MIN_ATTRIBUTE))) {
            String minValue = attributes.get(MIN_ATTRIBUTE).toString();

            result = result.replace("{" + MIN_ATTRIBUTE + "}", minValue);
        }

        if (!Objects.isNull(attributes.get(MAX_ATTRIBUTE))) {
            String maxValue = attributes.get(MAX_ATTRIBUTE).toString();

            result = result.replace("{" + MAX_ATTRIBUTE + "}", maxValue);
        }

        return result;
        
    }

}
