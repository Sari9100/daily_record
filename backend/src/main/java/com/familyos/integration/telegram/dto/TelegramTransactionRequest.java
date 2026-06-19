package com.familyos.integration.telegram.dto;

import com.familyos.ledger.dto.TransactionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Hermes 거래 등록 요청(내부). telegramUserId 로 행동 주체를 결정하고, 거래는 기존 규칙으로 검증한다.
 * source 는 서버가 TELEGRAM 으로 강제(클라 값 무시).
 */
public record TelegramTransactionRequest(
        @NotNull Long telegramUserId,
        @NotNull @Valid TransactionRequest transaction
) {
}
