package com.example.messenger.file.controller;

import com.example.messenger.auth.jwt.JwtAuthFilter.AuthPrincipal;
import com.example.messenger.common.exception.ApiException;
import com.example.messenger.common.exception.ErrorCode;
import com.example.messenger.common.response.ApiResponse;
import com.example.messenger.file.dto.UploadedFileResponse;
import com.example.messenger.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storage;

    /** 업로드. 인증 필요. */
    @PostMapping
    public ApiResponse<UploadedFileResponse> upload(@AuthenticationPrincipal AuthPrincipal me,
                                                    @RequestParam("file") MultipartFile file) {
        if (me == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return ApiResponse.ok(storage.store(file));
    }

    /**
     * 다운로드/뷰어. 학습용 단순화로 인증 없이 노출.
     * 파일명은 UUID 기반이라 추측이 어렵다는 점에 의존한다.
     * (운영용으로는 짧은 만료의 signed URL 또는 인증 필수로 가야 함)
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) throws IOException {
        Path p = storage.resolveStoredPath(filename);
        String mime = Files.probeContentType(p);
        if (mime == null) mime = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        Resource resource = new PathResource(p);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                // 이미지는 inline, 그 외는 attachment 가 자연스럽지만 일관되게 inline 처리
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + p.getFileName() + "\"")
                .body(resource);
    }
}
