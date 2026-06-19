package com.familyos.integration.google.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.repository.FamilyMembershipRepository;
import org.springframework.stereotype.Service;

/**
 * 백그라운드(스케줄러·푸시 webhook)에서 특정 구성원으로 행동하며 구글 동기화를 수행.
 *
 * <p>HTTP 요청이 없는 컨텍스트라 {@link FamilyContext} 가 비어 있으므로, 텔레그램 acting-as 패턴처럼
 * 대상 Person/family/role 로 컨텍스트를 세팅한 뒤 도메인 서비스({@link GoogleSyncService}/
 * {@link GooglePushService})를 호출한다. 그러면 {@code FamilyFilterAspect} 가 해당 서비스 진입 시
 * familyFilter 를 자동으로 켠다.
 *
 * <p>이 클래스 자체는 {@code @Transactional} 이 아니다 — 각 사용자의 sync/push 가 독립 트랜잭션이어야
 * 한 명의 실패가 다른 가족에 번지지 않는다(스케줄러가 사용자별로 try/catch).
 */
@Service
public class GoogleSyncWorker {

    private final FamilyMembershipRepository membershipRepository;
    private final GoogleSyncService syncService;
    private final GooglePushService pushService;

    public GoogleSyncWorker(FamilyMembershipRepository membershipRepository,
                            GoogleSyncService syncService,
                            GooglePushService pushService) {
        this.membershipRepository = membershipRepository;
        this.syncService = syncService;
        this.pushService = pushService;
    }

    /** 구글→우리 증분 동기화만 (푸시 알림 수신 시 — "변경 발생" 신호이므로 inbound 만 당긴다). */
    public void syncAs(GoogleSyncState state) {
        actingAs(state, syncService::syncForCurrentUser);
    }

    /** 양방향 한 사이클: 구글→우리 동기화 + 우리→구글 전송(정기 폴링). */
    public void syncAndPushAs(GoogleSyncState state) {
        actingAs(state, () -> {
            syncService.syncForCurrentUser();
            pushService.pushPendingForCurrentUser();
        });
    }

    private void actingAs(GoogleSyncState state, Runnable action) {
        AuthUser actor = resolveActor(state);
        AuthUser previous = FamilyContext.getOrNull();
        FamilyContext.set(actor);
        try {
            action.run();
        } finally {
            if (previous != null) {
                FamilyContext.set(previous);
            } else {
                FamilyContext.clear();
            }
        }
    }

    private AuthUser resolveActor(GoogleSyncState state) {
        FamilyMembership membership = membershipRepository
                .findByFamily_IdAndPerson_Id(state.getFamilyId(), state.getPersonId())
                .orElseThrow(() -> new NotFoundException("구성원 정보를 찾을 수 없습니다: personId=" + state.getPersonId()));
        // accountId 는 백그라운드 동기화에 불필요(null). 도메인 서비스는 personId/familyId 만 사용.
        return new AuthUser(state.getPersonId(), null, state.getFamilyId(), membership.getRole());
    }
}
