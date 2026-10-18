package com.example.mymangaapp.mymangaapp.service.storage;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

// Class này chỉ chuyên xử lý ảnh
@Component
public class ImageProcessor {

    public static final String OUTPUT_EXTENSION = "webp";
    public static final String OUTPUT_CONTENT_TYPE = "image/webp";

    private static final Set<String> VALID_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final float QUALITY = 0.8f; // chất lượng ảnh
    private static final long BYPASS_MAX_SIZE = 1024 * 1024;   // webp <= 1MB thì giữ nguyên
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int AVATAR_SIZE = 256; // kích thước chiều dài + rộng của avatar (áp dụng cả user và group)
    private static final int PAGE_MAX_WIDTH = 1200; // kíchthước chiều dài tối đa của ảnh cho chương truyện
    private static final int MANGA_COVER_MAX_WIDTH = 600; // kích thước chiều dài tối đa ảnh bìa truyện, dọc, giữ tỷ lệ gốc
    private static final int GROUP_COVER_WIDTH = 1200; // kích thước chiều dài ảnh bìa nhóm dịch, ngang 3:1
    private static final int GROUP_COVER_HEIGHT = 400; // kích thước chiều rộng ảnh bìa nhóm dịch

    /** Ảnh trang truyện: giới hạn chiều rộng 1200px, đổi sang webp. */
    public byte[] optimize(MultipartFile file) throws IOException {
        String extension = validate(file);

        if (OUTPUT_EXTENSION.equals(extension) && file.getSize() <= BYPASS_MAX_SIZE) {
            byte[] bytes = file.getBytes();
            if (!looksLikeWebp(bytes)) throw new AppException(ResponseCode.FILE_INVALID);
            return bytes;
        }

        BufferedImage source = readImage(file);

        return fitWidth(source, PAGE_MAX_WIDTH);
    }

    /** Avatar vuông 256x256, dùng cho cả user và nhóm dịch. */
    public byte[] toAvatar(MultipartFile file) throws IOException {
        validate(file);
        return cropToRatio(readImage(file), AVATAR_SIZE, AVATAR_SIZE);
    }

    /** Bìa truyện (dọc): chiều rộng tối đa 600px, giữ nguyên tỷ lệ, không cắt. */
    public byte[] toMangaCover(MultipartFile file) throws IOException {
        validate(file);
        return fitWidth(readImage(file), MANGA_COVER_MAX_WIDTH);
    }

    /** Bìa nhóm dịch (ngang): cắt giữa theo tỷ lệ 3:1, tối đa 1200x400. */
    public byte[] toGroupCover(MultipartFile file) throws IOException {
        validate(file);
        return cropToRatio(readImage(file), GROUP_COVER_WIDTH, GROUP_COVER_HEIGHT);
    }

    // ----------------------------- dùng chung ----------------------------- //

    // Giới hạn chiều rộng, giữ tỷ lệ; ảnh nhỏ hơn thì giữ nguyên, không phóng to
    private byte[] fitWidth(BufferedImage source, int maxWidth) throws IOException {
        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(source);
        // Ảnh nhỏ hơn max width thì giữ nguyên kích thước, không phóng to
        builder = source.getWidth() > maxWidth ? builder.width(maxWidth) : builder.scale(1.0);
        return encode(builder);
    }

    // Cắt ở giữa theo đúng tỷ lệ targetW:targetH rồi thu nhỏ về tối đa targetW x targetH
    private byte[] cropToRatio(BufferedImage source, int targetW, int targetH) throws IOException {
        double ratio = (double) targetW / targetH;

        // Vùng cắt lớn nhất có đúng tỷ lệ đích
        int regionW = source.getWidth();
        int regionH = (int) Math.round(regionW / ratio);
        if (regionH > source.getHeight()) {
            regionH = source.getHeight();
            regionW = (int) Math.round(regionH * ratio);
        }

        // Ảnh nhỏ hơn kích thước đích thì giữ nguyên, không phóng to
        double scale = Math.min(1.0, (double) regionW / targetW);
        int outW = (int) Math.round(targetW * scale);
        int outH = (int) Math.round(targetH * scale);

        return encode(Thumbnails.of(source)
                .sourceRegion(Positions.CENTER, regionW, regionH)
                .size(outW, outH));
    }

    private byte[] encode(Thumbnails.Builder<BufferedImage> builder) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        builder.outputQuality(QUALITY).outputFormat(OUTPUT_EXTENSION).toOutputStream(os);
        return os.toByteArray();
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResponseCode.FILE_REQUIRED);
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !VALID_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new AppException(ResponseCode.FILE_INVALID);
        }
        return extension.toLowerCase();
    }

    // Đọc kích thước TRƯỚC khi giải nén toàn bộ ảnh, đồng thời xác nhận đây đúng là file ảnh
    private BufferedImage readImage(MultipartFile file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new AppException(ResponseCode.FILE_INVALID);

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) throw new AppException(ResponseCode.FILE_SIZE_INVALID);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    // WebP là container RIFF: "RIFF" + 4 byte kích thước + "WEBP"
    private boolean looksLikeWebp(byte[] b) {
        return b.length > 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
