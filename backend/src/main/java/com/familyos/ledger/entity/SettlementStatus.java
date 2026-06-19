package com.familyos.ledger.entity;

/**
 * 정산 상태. 서버가 visibility 로 결정(클라 전송 무시).
 * NONE=정산 불필요(개인), PENDING=정산 필요(공동), SETTLED=정산 완료(확장 B).
 */
public enum SettlementStatus {
    NONE,
    PENDING,
    SETTLED
}
