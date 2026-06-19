package com.familyos.photo.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.entity.VisibleResource;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.diary.entity.Diary;
import com.familyos.diary.entity.DiarySubject;
import com.familyos.diary.repository.DiaryRepository;
import com.familyos.diary.repository.DiarySubjectRepository;
import com.familyos.photo.dto.CompleteRequest;
import com.familyos.photo.dto.PhotoResponse;
import com.familyos.photo.dto.PresignRequest;
import com.familyos.photo.dto.PresignResponse;
import com.familyos.photo.entity.Photo;
import com.familyos.photo.repository.PhotoRepository;
import com.familyos.storage.StorageService;
import com.familyos.storage.UrlSigner;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사진 — 2단계 업로드(presign→binary→complete) + 서명 URL 서빙.
 *
 * <p>권한은 연결 diary 의 visibility 를 따른다(standalone 은 작성자). 직접 URL 노출 금지 — 조회는
 * 인증 + visibility 판정 후 짧은 서명 URL 만 발급. 업로드/수정/삭제는 작성자 본인.
 */
@Service
@Transactional(readOnly = true)
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final DiaryRepository diaryRepository;
    private final DiarySubjectRepository diarySubjectRepository;
    private final StorageService storageService;
    private final UrlSigner urlSigner;
    private final VisibilityGuard visibilityGuard;
    private final SoftDeleteSupport softDeleteSupport;

    public PhotoService(PhotoRepository photoRepository,
                        DiaryRepository diaryRepository,
                        DiarySubjectRepository diarySubjectRepository,
                        StorageService storageService,
                        UrlSigner urlSigner,
                        VisibilityGuard visibilityGuard,
                        SoftDeleteSupport softDeleteSupport) {
        this.photoRepository = photoRepository;
        this.diaryRepository = diaryRepository;
        this.diarySubjectRepository = diarySubjectRepository;
        this.storageService = storageService;
        this.urlSigner = urlSigner;
        this.visibilityGuard = visibilityGuard;
        this.softDeleteSupport = softDeleteSupport;
    }

    @Transactional
    public PresignResponse presign(PresignRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();

        // 중복 제거: 같은 해시의 alive 사진이 있으면 기존 반환
        var dup = photoRepository.findByFamilyIdAndPhotoHash(familyId, req.photoHash());
        if (dup.isPresent()) {
            Photo p = dup.get();
            return new PresignResponse(p.getId(), null, p.getStorageKey(), true);
        }

        // diary 에 첨부 시: alive + 편집 권한(작성자) 확인
        if (req.diaryId() != null) {
            visibilityGuard.assertCanEdit(loadDiaryVisible(req.diaryId(), familyId), user);
        }

        String storageKey = storageService.generateKey(familyId, req.originalFilename(), req.takenAt());
        Photo photo = photoRepository.save(new Photo(
                familyId, req.diaryId(), storageKey, req.originalFilename(),
                req.photoHash(), req.takenAt(), req.fileSize(), req.mimeType()));

        return new PresignResponse(photo.getId(), "/api/v1/photos/" + photo.getId() + "/binary", storageKey, false);
    }

    /** 바이너리 업로드(서버 경유). photoHash 검증 후 SSD 저장. 작성자 본인만. */
    @Transactional
    public void uploadBinary(Long photoId, byte[] content) {
        AuthUser user = FamilyContext.require();
        Photo photo = loadOwned(photoId, user);
        String actual = sha256Hex(content);
        if (!actual.equalsIgnoreCase(photo.getPhotoHash())) {
            throw new BusinessException("업로드된 파일의 해시가 일치하지 않습니다.");
        }
        storageService.store(photo.getStorageKey(), content);
    }

    @Transactional
    public PhotoResponse complete(Long photoId, CompleteRequest req) {
        AuthUser user = FamilyContext.require();
        Photo photo = loadOwned(photoId, user);
        if (!storageService.exists(photo.getStorageKey())) {
            throw new BusinessException("업로드된 파일이 없습니다. 바이너리 업로드를 먼저 완료하세요.");
        }
        photo.completeMeta(req.width(), req.height());
        return toResponse(photo);
    }

    /** 조회 권한 판정 후 서명 URL 발급. */
    public PhotoResponse getWithSignedUrl(Long photoId) {
        AuthUser user = FamilyContext.require();
        Photo photo = photoRepository.findByIdAndFamilyId(photoId, user.familyId())
                .orElseThrow(() -> NotFoundException.of("사진", photoId));
        assertCanView(photo, user);
        return toResponse(photo);
    }

    @Transactional
    public void delete(Long photoId) {
        AuthUser user = FamilyContext.require();
        Photo photo = loadOwned(photoId, user);
        softDeleteSupport.softDelete(photo, photoRepository); // SSD 파일은 보존(이력)
    }

    /** raw 서빙 — 서명+만료만 검증(capability URL). FamilyContext 없이 동작(permitAll). */
    public RawPhoto serveRaw(Long photoId, long exp, String sig) {
        if (!urlSigner.isValid(photoId, exp, sig)) {
            throw new NotFoundException("사진을 찾을 수 없습니다."); // 위조/만료는 숨김
        }
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> NotFoundException.of("사진", photoId));
        byte[] content = storageService.load(photo.getStorageKey());
        return new RawPhoto(content, photo.getMimeType());
    }

    // ---- 내부 ----

    /** 작성자 본인 소유 사진 로드(편집/업로드/삭제용). 위반은 404 로 숨김. */
    private Photo loadOwned(Long photoId, AuthUser user) {
        Photo photo = photoRepository.findByIdAndFamilyId(photoId, user.familyId())
                .orElseThrow(() -> NotFoundException.of("사진", photoId));
        Long author = photo.getCreatedBy();
        if (author == null || !author.equals(user.personId())) {
            throw NotFoundException.of("사진", photoId);
        }
        return photo;
    }

    private void assertCanView(Photo photo, AuthUser user) {
        if (photo.getDiaryId() != null) {
            visibilityGuard.assertCanView(loadDiaryVisible(photo.getDiaryId(), user.familyId()), user);
            return;
        }
        // standalone: 작성자만
        Long author = photo.getCreatedBy();
        if (author == null || !author.equals(user.personId())) {
            throw NotFoundException.of("사진", photo.getId());
        }
    }

    private VisibleResource loadDiaryVisible(Long diaryId, Long familyId) {
        Diary diary = diaryRepository.findByIdAndFamilyId(diaryId, familyId)
                .orElseThrow(() -> NotFoundException.of("일상기록", diaryId));
        Set<Long> subjects = diarySubjectRepository.findByDiaryIdAndFamilyId(diaryId, familyId)
                .stream().map(DiarySubject::getPersonId).collect(Collectors.toSet());
        return new VisibleResource(diary.getVisibility(), diary.getCreatedBy(), subjects);
    }

    private PhotoResponse toResponse(Photo photo) {
        long exp = urlSigner.expiryEpochSecond();
        String sig = urlSigner.sign(photo.getId(), exp);
        String url = "/api/v1/photos/" + photo.getId() + "/raw?exp=" + exp + "&sig=" + sig;
        return new PhotoResponse(photo.getId(), photo.getDiaryId(), url,
                photo.getWidth(), photo.getHeight(), photo.getTakenAt(), photo.getMimeType());
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("해시 계산 실패", e);
        }
    }

    /** raw 서빙 결과. */
    public record RawPhoto(byte[] content, @Nullable String mimeType) {
    }
}
