package com.familyos.integration.telegram.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.integration.telegram.dto.TelegramTransactionRequest;
import com.familyos.integration.telegram.service.TelegramService;
import com.familyos.ledger.dto.StatisticsResponse;
import com.familyos.ledger.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Hermes 내부 API. 사용자 JWT 가 아니라 서비스 토큰({@code X-Service-Token})으로 인증
 * ({@code HermesServiceTokenFilter}). 행동 주체는 본문/파라미터의 telegramUserId 로 결정된다.
 */
@RestController
@RequestMapping("/api/v1/integrations/telegram")
public class TelegramController {

    private final TelegramService telegramService;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    /** 거래 등록(텍스트/영수증 파싱 후 확인 게이트를 거친 결과). source=TELEGRAM 강제. */
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> registerTransaction(@Valid @RequestBody TelegramTransactionRequest request) {
        return ApiResponse.ok(telegramService.registerTransaction(request.telegramUserId(), request.transaction()));
    }

    /** 조회 질의(통계). 예: "이번 달 식비 얼마?" → from/to 범위 집계. */
    @GetMapping("/query")
    public ApiResponse<StatisticsResponse> query(
            @RequestParam Long telegramUserId,
            @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant to,
            @RequestParam(required = false) @Nullable String scope) {
        return ApiResponse.ok(telegramService.statistics(telegramUserId, from, to, scope));
    }
}
