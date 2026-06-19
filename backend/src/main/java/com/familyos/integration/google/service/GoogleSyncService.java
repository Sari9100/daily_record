package com.familyos.integration.google.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.google.client.GoogleCalendarClient;
import com.familyos.integration.google.client.SyncTokenExpiredException;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.GoogleEventsResponse;
import com.familyos.integration.google.dto.GoogleSyncResult;
import com.familyos.integration.google.dto.MappedSchedule;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.oauth.GoogleOAuthClient;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 구글→우리 읽기 동기화 (docs/08 §1-3/1-4). 전체 동기화(최초) + 증분(syncToken).
 * 410(syncToken 만료) → 전체 재동기화 폴백. 페이지네이션 지원.
 *
 * <p>유입 일정: visibility 기본 PRIVATE, scheduleType EVENT, author=현재 사용자(수동 트리거가 JWT).
 * google_event_id 로 upsert, status=cancelled → soft-delete.
 */
@Service
public class GoogleSyncService {

    private static final String CALENDAR = "primary";

    private enum Outcome { CREATED, UPDATED, DELETED, SKIPPED }

    private final GoogleSyncStateRepository syncStateRepository;
    private final GoogleOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final GoogleCalendarClient calendarClient;
    private final GoogleEventMapper mapper;
    private final ScheduleRepository scheduleRepository;
    private final SoftDeleteSupport softDeleteSupport;

    public GoogleSyncService(GoogleSyncStateRepository syncStateRepository,
                             GoogleOAuthClient oauthClient,
                             TokenCipher tokenCipher,
                             GoogleCalendarClient calendarClient,
                             GoogleEventMapper mapper,
                             ScheduleRepository scheduleRepository,
                             SoftDeleteSupport softDeleteSupport) {
        this.syncStateRepository = syncStateRepository;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.calendarClient = calendarClient;
        this.mapper = mapper;
        this.scheduleRepository = scheduleRepository;
        this.softDeleteSupport = softDeleteSupport;
    }

    /** 현재 로그인 사용자의 구글 캘린더 동기화(수동 트리거). */
    @Transactional
    public GoogleSyncResult syncForCurrentUser() {
        AuthUser user = FamilyContext.require();
        GoogleSyncState state = syncStateRepository
                .findByPersonIdAndGoogleCalendarId(user.personId(), CALENDAR)
                .orElseThrow(() -> new NotFoundException("구글 캘린더 연동이 없습니다. 먼저 연결하세요."));
        if (state.getRefreshTokenEnc() == null) {
            throw new BusinessException("구글 연동이 해제되었습니다. 다시 연결해주세요.");
        }

        String refreshToken = tokenCipher.decrypt(state.getRefreshTokenEnc());
        String accessToken = oauthClient.refreshAccessToken(refreshToken).accessToken();
        Long familyId = user.familyId();

        if (state.getSyncToken() == null) {
            return runSync(state, familyId, accessToken, true);
        }
        try {
            return runSync(state, familyId, accessToken, false);
        } catch (SyncTokenExpiredException e) {
            return runSync(state, familyId, accessToken, true); // 410 → 전체 재동기화
        }
    }

    private GoogleSyncResult runSync(GoogleSyncState state, Long familyId, String accessToken, boolean full) {
        String syncToken = full ? null : state.getSyncToken();
        boolean showDeleted = !full;
        Instant now = Instant.now();
        int created = 0, updated = 0, deleted = 0;
        String pageToken = null;
        String nextSyncToken = null;

        do {
            // 첫 페이지만 syncToken, 이후엔 pageToken 만(구글은 둘 동시 지정 불가)
            String tokenForSync = (pageToken == null) ? syncToken : null;
            GoogleEventsResponse page = calendarClient.listEvents(accessToken, CALENDAR, tokenForSync, pageToken, showDeleted);
            for (GoogleEvent ev : page.itemsOrEmpty()) {
                switch (applyEvent(ev, familyId, now)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case DELETED -> deleted++;
                    case SKIPPED -> { }
                }
            }
            pageToken = page.nextPageToken();
            if (page.nextSyncToken() != null) {
                nextSyncToken = page.nextSyncToken();
            }
        } while (pageToken != null);

        if (full) {
            state.markFullSynced(nextSyncToken, now);
        } else {
            state.updateSyncToken(nextSyncToken, now);
        }
        return new GoogleSyncResult(full, created, updated, deleted);
    }

    private Outcome applyEvent(GoogleEvent ev, Long familyId, Instant now) {
        Optional<Schedule> existing = scheduleRepository.findByGoogleEventIdAndFamilyId(ev.id(), familyId);

        if ("cancelled".equals(ev.status())) {
            if (existing.isPresent()) {
                softDeleteSupport.softDelete(existing.get(), scheduleRepository);
                return Outcome.DELETED;
            }
            return Outcome.SKIPPED;
        }

        MappedSchedule m = mapper.map(ev);
        if (m == null) {
            return Outcome.SKIPPED;
        }
        if (existing.isPresent()) {
            Schedule s = existing.get();
            s.applyGoogleEvent(m.title(), m.description(), m.location(),
                    m.startedAt(), m.endedAt(), m.startDate(), m.endDate(), m.allDay(), m.recurrenceRule());
            s.markGoogleSynced(ev.id(), now);
            return Outcome.UPDATED;
        }
        Schedule created = new Schedule(familyId, m.title(), m.description(), m.location(),
                m.startedAt(), m.endedAt(), m.startDate(), m.endDate(), m.allDay(),
                Visibility.PRIVATE, ScheduleType.EVENT, m.recurrenceRule(), null);
        created.markGoogleSynced(ev.id(), now);
        scheduleRepository.save(created);
        return Outcome.CREATED;
    }
}
