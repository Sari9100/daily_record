package com.familyos.ledger.service;

import com.familyos.ledger.dto.TransactionResponse.AccountRef;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.AccountOwnerType;
import org.jspecify.annotations.Nullable;

/**
 * 거래 응답의 결제계좌 마스킹 규칙 (순수 함수 — 테스트 용이).
 *
 * <p>"무엇에 썼나"(금액·카테고리)는 공유하되 "어느 카드"(결제계좌)는 개인. 따라서:
 * <ul>
 *   <li>공용 계좌(FAMILY) → 가족 모두에게 표시</li>
 *   <li>개인 계좌(PERSON) → 소유자 본인에게만 표시, 타인에겐 마스킹(null)</li>
 *   <li>계좌 미참조/삭제(조회 불가) → null</li>
 * </ul>
 * (삭제된 계좌의 "(삭제됨)" 라벨은 과거 데이터 보존이 중요한 통계·타임라인(MyBatis) 단계에서 처리)
 */
final class AccountMasking {

    private AccountMasking() {
    }

    static @Nullable AccountRef visibleRef(@Nullable Account account, Long viewerPersonId) {
        if (account == null) {
            return null;
        }
        boolean visible = account.getOwnerType() == AccountOwnerType.FAMILY
                || (account.getOwnerType() == AccountOwnerType.PERSON
                && viewerPersonId.equals(account.getOwnerPersonId()));
        return visible ? new AccountRef(account.getId(), account.getName()) : null;
    }
}
