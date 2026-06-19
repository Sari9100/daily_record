package com.familyos.integration.google.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.integration.google.dto.GoogleConnectResponse;
import com.familyos.integration.google.dto.GooglePushResult;
import com.familyos.integration.google.dto.GoogleSyncResult;
import com.familyos.integration.google.service.GoogleConnectService;
import com.familyos.integration.google.service.GooglePushService;
import com.familyos.integration.google.service.GoogleSyncService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구글 캘린더 연동 API.
 * <ul>
 *   <li>{@code POST /connect} (JWT) — 동의 URL 발급</li>
 *   <li>{@code GET /callback} (permitAll) — 구글 리디렉션 수신·토큰 저장. state HMAC 로 주체 검증</li>
 *   <li>{@code DELETE} (JWT) — 연동 해제</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/integrations/google")
public class GoogleController {

    private final GoogleConnectService connectService;
    private final GoogleSyncService syncService;
    private final GooglePushService pushService;

    public GoogleController(GoogleConnectService connectService, GoogleSyncService syncService,
                            GooglePushService pushService) {
        this.connectService = connectService;
        this.syncService = syncService;
        this.pushService = pushService;
    }

    @PostMapping("/connect")
    public ApiResponse<GoogleConnectResponse> connect() {
        return ApiResponse.ok(new GoogleConnectResponse(connectService.connect()));
    }

    /** 수동 동기화 트리거(구글→우리). 최초엔 전체, 이후 증분(syncToken). */
    @PostMapping("/sync")
    public ApiResponse<GoogleSyncResult> sync() {
        return ApiResponse.ok(syncService.syncForCurrentUser());
    }

    /** 우리→구글 전송(Phase 2). 로컬 PENDING 일정을 구글에 생성/수정. */
    @PostMapping("/push")
    public ApiResponse<GooglePushResult> push() {
        return ApiResponse.ok(pushService.pushPendingForCurrentUser());
    }

    /** 브라우저 리디렉션 대상 — 사람이 보는 화면이라 간단한 텍스트로 응답(프론트 연동 시 리디렉션으로 교체). */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam(required = false) @Nullable String code,
                           @RequestParam(required = false) @Nullable String state,
                           @RequestParam(required = false) @Nullable String error) {
        if (error != null) {
            return "구글 연동이 취소/실패했습니다: " + error;
        }
        if (code == null || state == null) {
            return "잘못된 콜백 요청입니다.";
        }
        connectService.handleCallback(code, state);
        return "구글 캘린더 연동이 완료되었습니다. 앱으로 돌아가세요.";
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect() {
        connectService.disconnect();
        return ApiResponse.ok();
    }
}
