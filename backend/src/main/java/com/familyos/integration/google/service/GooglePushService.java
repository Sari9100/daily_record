package com.familyos.integration.google.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.google.client.GoogleCalendarClient;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.GoogleEventWrite;
import com.familyos.integration.google.dto.GooglePushResult;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.oauth.GoogleOAuthClient;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.SyncStatus;
import com.familyos.schedule.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 우리→구글 전송 (Phase 2). 로컬에서 PENDING 표시된 일정을 구글로 events.insert/update.
 *
 * <p>무한루프 가드: PENDING 은 우리 앱 액션(ScheduleService)에서만 설정되고, 구글 유입 변경은 SYNCED 라
 * 여기 대상에 안 들어온다. 전송 성공 시 google_event_id 저장 + SYNCED 로 전환 → 재전송되지 않는다.
 *
 * <p>작성자 본인의 일정만 본인 구글 캘린더(primary)로 전송한다.
 */
@Service
public class GooglePushService {

    private static final String CALENDAR = "primary";

    private final GoogleSyncStateRepository syncStateRepository;
    private final GoogleOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final GoogleCalendarClient calendarClient;
    private final GoogleEventWriteMapper writeMapper;
    private final ScheduleRepository scheduleRepository;

    public GooglePushService(GoogleSyncStateRepository syncStateRepository,
                             GoogleOAuthClient oauthClient,
                             TokenCipher tokenCipher,
                             GoogleCalendarClient calendarClient,
                             GoogleEventWriteMapper writeMapper,
                             ScheduleRepository scheduleRepository) {
        this.syncStateRepository = syncStateRepository;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.calendarClient = calendarClient;
        this.writeMapper = writeMapper;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public GooglePushResult pushPendingForCurrentUser() {
        AuthUser user = FamilyContext.require();
        GoogleSyncState state = syncStateRepository
                .findByPersonIdAndGoogleCalendarId(user.personId(), CALENDAR)
                .orElseThrow(() -> new NotFoundException("구글 캘린더 연동이 없습니다. 먼저 연결하세요."));
        if (state.getRefreshTokenEnc() == null) {
            throw new BusinessException("구글 연동이 해제되었습니다. 다시 연결해주세요.");
        }

        String refreshToken = tokenCipher.decrypt(state.getRefreshTokenEnc());
        String accessToken = oauthClient.refreshAccessToken(refreshToken).accessToken();

        List<Schedule> pending = scheduleRepository.findByFamilyIdAndCreatedByAndSyncStatus(
                user.familyId(), user.personId(), SyncStatus.PENDING);

        int inserted = 0, updated = 0, deleted = 0;
        Instant now = Instant.now();

        // (1) 생성/수정 — alive 한 PENDING
        for (Schedule s : pending) {
            GoogleEventWrite body = writeMapper.toWrite(s);
            if (s.getGoogleEventId() == null) {
                GoogleEvent created = calendarClient.insertEvent(accessToken, CALENDAR, body);
                s.markGoogleSynced(created.id(), now);
                inserted++;
            } else {
                calendarClient.updateEvent(accessToken, CALENDAR, s.getGoogleEventId(), body);
                s.markGoogleSynced(s.getGoogleEventId(), now);
                updated++;
            }
        }

        // (2) 삭제 전송 — soft-delete 됐지만 구글엔 살아있는(google_event_id 보유) PENDING
        List<Schedule> toDelete = scheduleRepository.findDeletedPendingForGoogle(user.familyId(), user.personId());
        for (Schedule s : toDelete) {
            calendarClient.deleteEvent(accessToken, CALENDAR, s.getGoogleEventId());
            scheduleRepository.markDeletionPushed(s.getId(), now); // 삭제된 행이라 네이티브 UPDATE 로 전이
            deleted++;
        }
        return new GooglePushResult(inserted, updated, deleted);
    }
}
