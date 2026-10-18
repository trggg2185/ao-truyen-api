package com.example.mymangaapp.mymangaapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ResponseCode {

    /*
        Mã gồm 4 chữ số: [loại][domain][số thứ tự 2 chữ số]

        Loại (chữ số 1):
            0 - Success
            1 - Authentication
            2 - Authorization
            3 - Validation
            4 - Resource
            5 - Business
            6 - Request (rate limit, method...)
            9 - Internal

        Domain (chữ số 2):
            0 - Chung (không thuộc domain nào)
            1 - User
            2 - Role / Permission
            3 - TransGroup (nhóm dịch + các yêu cầu liên quan)
            4 - Manga
            5 - Chapter
            6 - Category
            7 - File / Storage

        Thêm response mới: chỉ lấy số tiếp theo trong domain tương ứng và
        chèn vào cuối khối domain đó, KHÔNG sửa mã cũ.
    */

    SUCCESS("0000", "Thành công!", HttpStatus.OK),

    // ===================== 1xxx - AUTHENTICATION =====================
    UNAUTHENTICATED("1001", "Chưa được xác thực!", HttpStatus.UNAUTHORIZED),
    PASSWORD_INCORRECT("1002", "Mật khẩu không chính xác!", HttpStatus.UNAUTHORIZED),

    // ===================== 2xxx - AUTHORIZATION =====================
    UNAUTHORIZED("2001", "Không có quyền!", HttpStatus.FORBIDDEN),
    CANNOT_MODIFY_ADMIN("2002", "Không thể thao tác lên admin khác!", HttpStatus.FORBIDDEN),

    // ===================== 3xxx - VALIDATION =====================
    // --- Chung ---
    FIELD_VALUE_INVALID("3001", "Giá trị trường không hợp lệ!", HttpStatus.BAD_REQUEST),

    // --- User ---
    USERNAME_INVALID("3101", "Tên người dùng phải có ít nhất {min} ký tự và tối đa {max} Ký tự!", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID("3102", "Mật khẩu phải có ít nhất {min} ký tự và tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    EMAIL_INVALID("3103", "Email không đúng định dạng!", HttpStatus.BAD_REQUEST),
    BIO_INVALID("3104", "Thông tin cá nhân có tối đa {max} Ký tự!", HttpStatus.BAD_REQUEST),
    USERNAME_REQUIRED("3105", "Tên người dùng không được để trống!", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED("3106", "Mật khẩu không được để trống!", HttpStatus.BAD_REQUEST),
    TOKEN_REQUIRED("3107", "Chuỗi token không được để trống!", HttpStatus.BAD_REQUEST),

    // --- Role / Permission ---
    ROLE_NAME_REQUIRED("3201", "Tên vai trò không được để trống!", HttpStatus.BAD_REQUEST),
    ROLE_NAME_INVALID("3202", "Tên vai trò có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    ROLE_DESCRIPTION_INVALID("3203", "Mô tả vai trò có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    PERMISSIONS_REQUIRED("3204", "Danh sách quyền không được để trống!", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_REQUIRED("3205", "Tên quyền không được để trống!", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_INVALID("3206", "Tên quyền có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    PERMISSION_DESCRIPTION_INVALID("3207", "Mô tả quyền có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    PERMISSION_INVALID("3208", "Các quyền không hợp lệ! (Các quyền không tồn tại hoặc mảng các quyền của request rỗng)", HttpStatus.BAD_REQUEST),
    PERMISSION_REQUIRED("3209", "Mảng các quyền không được để trống!", HttpStatus.BAD_REQUEST),

    // --- TransGroup ---
    TRANSGROUP_NAME_INVALID("3301", "Tên nhóm dịch ít nhất phải có {min} ký tự và tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_NAME_REQUIRED("3302", "Tên nhóm dịch không được để trống!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_DESCRIPTION_INVALID("3303", "Mô tả nhóm dịch có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_COVER_IMAGE_INVALID("3304", "Ảnh bìa nhóm dịch có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),

    // --- Manga ---
    MANGA_NAME_INVALID("3401", "Tên truyện có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    AUTHORS_NAME_INVALID("3402", "Tên tác giả có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    GENRES_REQUIRED("3403", "Thể loại truyện không được để trống!", HttpStatus.BAD_REQUEST),
    MANGA_STATUS_REQUIRED("3404", "Trạng thái truyện không được để trống!", HttpStatus.BAD_REQUEST),
    MANGA_DESCRIPTION_INVALID("3405", "Mô tả truyện có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    MANGA_COVER_IMAGE_INVALID("3406", "Ảnh bìa truyện có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),

    // --- Chapter ---
    CHAPTER_INDEX_REQUIRED("3501", "Số chương không được để trống!", HttpStatus.BAD_REQUEST),
    CHAPTER_INDEX_POSITIVE("3502", "Số chương phải là số nguyên dương!", HttpStatus.BAD_REQUEST),
    CHAPTER_TITLE_INVALID("3503", "Tiêu đề chương có tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    PAGE_URLS_REQUIRED("3504", "Danh sách url của ảnh không được để trống!", HttpStatus.BAD_REQUEST),
    PAGE_URLS_SIZE_INVALID("3505", "Số lượng url tối đa là {max}!", HttpStatus.BAD_REQUEST),

    // --- Category ---
    CATEGORY_NAME_REQUIRED("3601", "Tên thể loại không được để trống!", HttpStatus.BAD_REQUEST),
    CATEGORY_NAME_INVALID("3602", "Tên thể loại phải có ít nhất {min} ký tự và tối đa {max} ký tự!", HttpStatus.BAD_REQUEST),
    CATEGORIES_REQUIRED("3603", "Danh sách thể loại không được để trống!", HttpStatus.BAD_REQUEST),

    // --- File ---
    FILE_REQUIRED("3701", "File không được để trống!", HttpStatus.BAD_REQUEST),
    FILE_SIZE_INVALID("3702", "Kích thước file quá lớn!", HttpStatus.BAD_REQUEST),

    // ===================== 4xxx - RESOURCE (NOT FOUND) =====================
    // --- User ---
    USER_NOT_FOUND("4101", "Người dùng không tồn tại!", HttpStatus.NOT_FOUND),

    // --- Role / Permission ---
    ROLE_NOT_FOUND("4201", "Vai trò không tồn tại!", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND("4202", "Quyền không tồn tại!", HttpStatus.NOT_FOUND),

    // --- TransGroup ---
    TRANSGROUP_NOT_FOUND("4301", "Nhóm dịch không tồn tại!", HttpStatus.NOT_FOUND),
    TRANSGROUP_JOIN_REQUEST_NOT_FOUND("4302", "Yêu cầu vào nhóm không tồn tại!", HttpStatus.NOT_FOUND),
    TRANSGROUP_CREATION_REQUEST_NOT_FOUND("4303", "Yêu cầu tạo nhóm dịch không tồn tại!", HttpStatus.NOT_FOUND),

    // --- Manga ---
    MANGA_NOT_FOUND("4401", "Manga không tồn tại!", HttpStatus.NOT_FOUND),

    // --- Chapter ---
    CHAPTER_NOT_FOUND("4501", "Chương này không tồn tại!", HttpStatus.NOT_FOUND),

    // --- Category ---
    CATEGORY_NOT_FOUND("4601", "Thể loại không tồn tại!", HttpStatus.NOT_FOUND),

    // ===================== 5xxx - BUSINESS =====================
    // --- Chung ---
    DATA_INTEGRITY_VIOLATION("5001", "Dữ liệu vi phạm ràng buộc!", HttpStatus.BAD_REQUEST),

    // --- User ---
    USERNAME_ALREADY_EXISTED("5101", "Tên người dùng đã tồn tại!", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_EXISTED("5102", "Email đã tồn tại!", HttpStatus.BAD_REQUEST),

    // --- Role / Permission ---
    ROLE_NAME_ALREADY_EXISTED("5201", "Tên vai trò đã tồn tại!", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_ALREADY_EXISTED("5202", "Tên quyền đã tồn tại!", HttpStatus.BAD_REQUEST),

    // --- TransGroup ---
    TRANSGROUP_NAME_ALREADY_EXISTED("5301", "Tên nhóm dịch đã tồn tại!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_STATUS_INVALID("5302", "Nhóm dịch đã được chấp nhận, bị từ chối hoặc đã bị xoá!", HttpStatus.BAD_REQUEST),
    USER_ALREADY_IN_GROUP("5303", "Người dùng đã trong nhóm dịch!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_NOT_APPROVED("5304", "Nhóm dịch chưa được duyệt!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_JOIN_REQUEST_STATUS_INVALID("5305", "Yêu cầu vào nhóm đã được chập nhận hoặc bị tự chối!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_JOIN_REQUEST_ALREADY_EXISTED("5306", "Yêu cầu vào nhóm dịch đã tồn tại!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_CREATION_REQUEST_ALREADY_EXISTED("5307", "Yêu cầu tạo nhóm dịch đã tồn tại!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_CREATION_REQUEST_STATUS_INVALID("5308", "Yêu cầu tạo nhóm dịch đã được chấp nhận hoặc bị từ chối!", HttpStatus.BAD_REQUEST),
    TRANSGROUP_ALREADY_DELETED("5309", "Nhóm dịch đã bị xoá!", HttpStatus.BAD_REQUEST),

    // ------ Manga ----------
    MANGA_MUST_HAVE_GROUP("5401", "Truyện phải có ít nhất 1 nhóm dịch!", HttpStatus.BAD_REQUEST),

    // --- Chapter ---
    CHAPTER_INDEX_ALREADY_EXISTED("5501", "Chapter index đã tồn tại!", HttpStatus.BAD_REQUEST),

    // --- Category ---
    CATEGORY_NAME_ALREADY_EXISTED("5601", "Tên thể loại đã tồn tại!", HttpStatus.BAD_REQUEST),

    // --- File ---
    FILE_INVALID("5701", "File không hợp lệ!", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("5702", "Upload file thất bại!", HttpStatus.BAD_REQUEST),
    FILE_COPY_FAILED("5703", "Copy file thất bại!", HttpStatus.BAD_REQUEST),
    FILE_DELETE_FAILED("5704", "Xoá file thất bại!", HttpStatus.BAD_REQUEST),

    // ===================== 6xxx - REQUEST =====================
    RATE_LIMIT_EXCEEDED("6001", "Đạt giới hạn thao tác, vui lòng thử lại sau!", HttpStatus.TOO_MANY_REQUESTS),
    METHOD_NOT_ALLOWED("6002", "Phương thức không được phép!", HttpStatus.METHOD_NOT_ALLOWED),

    // ===================== 9xxx - INTERNAL =====================
    // --- Chung ---
    UNCATEGORIZED_ERROR("9001", "Lỗi không xác định!", HttpStatus.INTERNAL_SERVER_ERROR),
    ENUM_KEY_INVALID("9002", "Enum key không hợp lệ!", HttpStatus.INTERNAL_SERVER_ERROR),
    RUNTIME_EXCEPTION("9003", "Lỗi ngoại lệ runtime!", HttpStatus.INTERNAL_SERVER_ERROR),
    URL_INVALID("9004", "Lỗi url không hợp lệ!", HttpStatus.INTERNAL_SERVER_ERROR),
    HTTP_MESSAGE_NOT_READABLE("9005", "Lỗi không thể đọc message HTTP!", HttpStatus.INTERNAL_SERVER_ERROR),

    // --- File / Storage ---
    STORAGE_SERVICE_UNAVAILABLE("9701", "Dịch vụ lưu trũ không khả dụng!", HttpStatus.SERVICE_UNAVAILABLE),
    STORAGE_SERVICE_ERROR("9702", "Lỗi khi tương tác với dịch vụ lưu trũ!", HttpStatus.INTERNAL_SERVER_ERROR);

    String code;
    String message;
    HttpStatusCode httpStatusCode;

}
