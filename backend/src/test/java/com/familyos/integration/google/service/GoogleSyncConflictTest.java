package com.familyos.integration.google.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.integration.google.client.GoogleCalendarClient;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.GoogleEventsResponse;
import com.familyos.integration.google.dto.MappedSchedule;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.oauth.GoogleOAuthClient;
import com.familyos.integration.google.oauth.GoogleTokenResponse;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.SyncStatus;
import com.familyos.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 양방향 충돌(docs §1-7) — 로컬 미전송(PENDING) 일정에 구글 변경이 유입될 때 last-write-wins + CONFLICT 마킹.
 */
@ExtendWith(MockitoExtension.class)
class GoogleSyncConflictTest {

    @Mock GoogleSyncStateRepository syncStateRepository;
    @Mock GoogleOAuthClient oauthClient;
    @Mock TokenCipher tokenCipher;
    @Mock GoogleCalendarClient calendarClient;
    @Mock GoogleEventMapper mapper;
    @Mock ScheduleRepository scheduleRepository;
    @Mock SoftDeleteSupport softDeleteSupport;

    @InjectMocks GoogleSyncService syncService;

    private static final Long FAMILY = 1L;
    private static final Long PERSON = 10L;

    @BeforeEach
    void setUp() {
        FamilyContext.set(new AuthUser(PERSON, null, FAMILY, FamilyRole.PARENT));

        GoogleSyncState state = org.mockito.Mockito.mock(GoogleSyncState.class);
        when(state.getRefreshTokenEnc()).thenReturn(new byte[]{1});
        when(state.getSyncToken()).thenReturn("tok"); // 증분 경로
        when(syncStateRepository.findByPersonIdAndGoogleCalendarId(PERSON, "primary"))
                .thenReturn(Optional.of(state));

        when(tokenCipher.decrypt(any())).thenReturn("rt");
        GoogleTokenResponse token = org.mockito.Mockito.mock(GoogleTokenResponse.class);
        lenient().when(token.accessToken()).thenReturn("at");
        when(oauthClient.refreshAccessToken("rt")).thenReturn(token);
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private GoogleEvent changedEvent(String updatedRfc3339) {
        return new GoogleEvent("g1", "confirmed", "구글 제목", null, null, null, null, null, updatedRfc3339);
    }

    private MappedSchedule mapped() {
        return new MappedSchedule("구글 제목", null, null,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T01:00:00Z"),
                null, null, false, null);
    }

    private void singlePage(GoogleEvent ev) {
        when(calendarClient.listEvents(eq("at"), eq("primary"), any(), any(), anyBoolean()))
                .thenReturn(new GoogleEventsResponse(List.of(ev), null, "tok2"));
        when(mapper.map(ev)).thenReturn(mapped());
    }

    @Test
    void 구글이_더_최신이면_구글내용_채택하고_CONFLICT_마킹() {
        GoogleEvent ev = changedEvent("2026-06-10T00:00:00Z"); // 구글 수정이 로컬보다 나중
        singlePage(ev);

        Schedule local = org.mockito.Mockito.mock(Schedule.class);
        when(local.getSyncStatus()).thenReturn(SyncStatus.PENDING);
        when(local.getUpdatedAt()).thenReturn(Instant.parse("2026-06-01T00:00:00Z"));
        when(scheduleRepository.findByGoogleEventIdAndFamilyId("g1", FAMILY)).thenReturn(Optional.of(local));

        syncService.syncForCurrentUser();

        verify(local).applyGoogleEvent(eq("구글 제목"), isNull(), isNull(), any(), any(), isNull(), isNull(), eq(false), isNull());
        verify(local).markConflict(eq("g1"), any());
        verify(local, never()).markGoogleSynced(any(), any());
    }

    @Test
    void 로컬이_더_최신이면_보존하고_적용하지_않는다() {
        GoogleEvent ev = changedEvent("2026-06-01T00:00:00Z"); // 구글 수정이 로컬보다 이전
        singlePage(ev);

        Schedule local = org.mockito.Mockito.mock(Schedule.class);
        when(local.getSyncStatus()).thenReturn(SyncStatus.PENDING);
        when(local.getUpdatedAt()).thenReturn(Instant.parse("2026-06-10T00:00:00Z"));
        when(scheduleRepository.findByGoogleEventIdAndFamilyId("g1", FAMILY)).thenReturn(Optional.of(local));

        syncService.syncForCurrentUser();

        verify(local, never()).applyGoogleEvent(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(local, never()).markConflict(any(), any());
        verify(local, never()).markGoogleSynced(any(), any());
    }

    @Test
    void PENDING_아니면_정상_업데이트로_SYNCED() {
        GoogleEvent ev = changedEvent("2026-06-10T00:00:00Z");
        singlePage(ev);

        Schedule local = org.mockito.Mockito.mock(Schedule.class);
        when(local.getSyncStatus()).thenReturn(SyncStatus.SYNCED);
        when(scheduleRepository.findByGoogleEventIdAndFamilyId("g1", FAMILY)).thenReturn(Optional.of(local));

        syncService.syncForCurrentUser();

        verify(local).applyGoogleEvent(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(local).markGoogleSynced(eq("g1"), any());
        verify(local, never()).markConflict(any(), any());
    }
}
