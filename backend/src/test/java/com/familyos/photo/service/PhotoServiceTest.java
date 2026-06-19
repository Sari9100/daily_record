package com.familyos.photo.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.diary.repository.DiaryRepository;
import com.familyos.diary.repository.DiarySubjectRepository;
import com.familyos.photo.dto.PresignRequest;
import com.familyos.photo.dto.PresignResponse;
import com.familyos.photo.entity.Photo;
import com.familyos.photo.repository.PhotoRepository;
import com.familyos.storage.StorageService;
import com.familyos.storage.UrlSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PhotoService 핵심 규칙(필수): photo_hash 중복 제거(중복 업로드 방지). 빌드/실행은 이 환경에서 미검증.
 *
 * <p>업로드 해시 검증(uploadBinary)·서명 URL 권한은 createdBy(auditing) 의존이라 순수 단위테스트가 어렵다 →
 * 실DB 통합테스트로 커버 권장.
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    private static final Long FAMILY_ID = 1L;

    @Mock PhotoRepository photoRepository;
    @Mock DiaryRepository diaryRepository;
    @Mock DiarySubjectRepository diarySubjectRepository;
    @Mock StorageService storageService;
    @Mock UrlSigner urlSigner;
    @Mock VisibilityGuard visibilityGuard;
    @Mock SoftDeleteSupport softDeleteSupport;

    PhotoService service;

    @BeforeEach
    void setUp() {
        service = new PhotoService(photoRepository, diaryRepository, diarySubjectRepository,
                storageService, urlSigner, visibilityGuard, softDeleteSupport);
        FamilyContext.set(new AuthUser(1L, 10L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    @Test
    void photoHash_중복이면_기존_사진을_반환하고_새로_저장하지_않는다() {
        Photo existing = new Photo(FAMILY_ID, null, "family_1/2026/06/x.jpg", "a.jpg",
                "hash123", null, 100L, "image/jpeg");
        when(photoRepository.findByFamilyIdAndPhotoHash(FAMILY_ID, "hash123")).thenReturn(Optional.of(existing));

        PresignResponse res = service.presign(new PresignRequest("a.jpg", "image/jpeg", 100L, "hash123", null, null));

        assertThat(res.duplicated()).isTrue();
        assertThat(res.uploadUrl()).isNull();
        verify(photoRepository, never()).save(any());
    }
}
