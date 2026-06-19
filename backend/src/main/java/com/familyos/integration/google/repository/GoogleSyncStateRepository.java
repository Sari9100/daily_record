package com.familyos.integration.google.repository;

import com.familyos.integration.google.entity.GoogleSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoogleSyncStateRepository extends JpaRepository<GoogleSyncState, Long> {

    Optional<GoogleSyncState> findByPersonIdAndGoogleCalendarId(Long personId, String googleCalendarId);

    /** 정기 폴링/채널 갱신 대상 — refresh token 이 살아있는(연동된) 상태. */
    List<GoogleSyncState> findByRefreshTokenEncIsNotNull();
}
