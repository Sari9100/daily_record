package com.familyos.photo.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.photo.dto.CompleteRequest;
import com.familyos.photo.dto.PhotoResponse;
import com.familyos.photo.dto.PresignRequest;
import com.familyos.photo.dto.PresignResponse;
import com.familyos.photo.service.PhotoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /** 1단계: 업로드 의도 등록(+중복 제거). */
    @PostMapping("/presign")
    public ApiResponse<PresignResponse> presign(@Valid @RequestBody PresignRequest request) {
        return ApiResponse.ok(photoService.presign(request));
    }

    /** 2단계: 바이너리 업로드(서버 경유). presign 응답의 uploadUrl 로 PUT. */
    @PutMapping(value = "/{id}/binary", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<Void> uploadBinary(@PathVariable Long id, @RequestBody byte[] content) {
        photoService.uploadBinary(id, content);
        return ApiResponse.ok();
    }

    /** 3단계: 업로드 확정 + 메타(width/height). */
    @PostMapping("/{id}/complete")
    public ApiResponse<PhotoResponse> complete(@PathVariable Long id, @Valid @RequestBody CompleteRequest request) {
        return ApiResponse.ok(photoService.complete(id, request));
    }

    /** 메타 + 서명 조회 URL 반환(인증 + visibility 판정). */
    @GetMapping("/{id}")
    public ApiResponse<PhotoResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(photoService.getWithSignedUrl(id));
    }

    /** 서명 URL raw 서빙 — exp/sig 만 검증(permitAll, capability URL). */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id,
                                      @RequestParam long exp,
                                      @RequestParam String sig) {
        PhotoService.RawPhoto raw = photoService.serveRaw(id, exp, sig);
        MediaType mediaType = raw.mimeType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(raw.mimeType());
        return ResponseEntity.ok().contentType(mediaType).body(raw.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        photoService.delete(id);
        return ApiResponse.ok();
    }
}
