package com.example.messenger.file.service;

import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.file.dto.UploadedFileResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * 로컬 디스크 기반 파일 저장소. 학습용으로 단순화.
 *  - 저장 경로: {file.upload-dir}/{uuid}.{ext}
 *  - 외부 노출 URL: /api/files/{uuid}.{ext}
 *  - 본 클래스는 ‘저장’과 ‘찾기’만 책임. 권한 검사는 FileController가 담당.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> IMAGE_MIME = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final long MAX_FILE_SIZE  = 50L * 1024 * 1024; // 50MB

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload-dir:./var/uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(uploadDir);
        log.info("Upload dir = {}", uploadDir);
    }

    public UploadedFileResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.");
        }
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase();
        boolean isImage = IMAGE_MIME.contains(mime);
        long size = file.getSize();

        if (isImage && size > MAX_IMAGE_SIZE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미지는 10MB 이하만 가능합니다.");
        }
        if (!isImage && size > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "파일은 50MB 이하만 가능합니다.");
        }

        String original = sanitizeFilename(file.getOriginalFilename());
        String ext = extractExtension(original, mime);
        String storedName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);

        try {
            Path target = uploadDir.resolve(storedName).normalize();
            // path traversal 방어: 저장 경로가 uploadDir 밖으로 못 나가게
            if (!target.startsWith(uploadDir)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "잘못된 파일 경로입니다.");
            }
            file.transferTo(target);
        } catch (IOException e) {
            log.error("file save error", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "파일 저장에 실패했습니다.");
        }

        return new UploadedFileResponse(
                "/api/files/" + storedName,
                original,
                mime,
                size,
                isImage ? "IMAGE" : "FILE"
        );
    }

    /** 다운로드/뷰어용 절대 경로 반환. 존재 여부 체크 포함. */
    public Path resolveStoredPath(String filename) {
        String safe = sanitizeFilename(filename);
        Path p = uploadDir.resolve(safe).normalize();
        if (!p.startsWith(uploadDir)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "잘못된 파일 경로입니다.");
        }
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "파일을 찾을 수 없습니다.");
        }
        return p;
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "file";
        // 경로 분리자 제거 + 양 끝 트림
        String name = raw.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.trim();
        return name.isEmpty() ? "file" : name;
    }

    private static String extractExtension(String original, String mime) {
        int dot = original.lastIndexOf('.');
        if (dot > 0 && dot < original.length() - 1) {
            String ext = original.substring(dot + 1).toLowerCase();
            // 허용 확장자: 영숫자만, 1~5자
            if (ext.matches("[a-z0-9]{1,5}")) return ext;
        }
        // mime 으로 추정
        return switch (mime) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }
}
