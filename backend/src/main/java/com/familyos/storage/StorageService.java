package com.familyos.storage;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 파일 저장 추상화. 로컬 SSD 구현({@code LocalSsdStorage})이 기본, 향후 R2 등으로 동일 인터페이스 교체.
 *
 * <p>storageKey 는 상대 키(예: {@code family_1/2026/06/uuid.jpg}). 절대 경로/직접 URL 을 외부에 노출하지 않는다.
 */
public interface StorageService {

    /** 신규 객체용 상대 키 생성. family 별 디렉토리 격리. */
    String generateKey(Long familyId, String originalFilename, @Nullable Instant takenAt);

    void store(String storageKey, byte[] content);

    byte[] load(String storageKey);

    boolean exists(String storageKey);
}
