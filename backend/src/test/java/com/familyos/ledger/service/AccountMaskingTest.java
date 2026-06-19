package com.familyos.ledger.service;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.dto.TransactionResponse.AccountRef;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.AccountOwnerType;
import com.familyos.ledger.entity.AssetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 결제계좌 마스킹: 공용은 전원 표시, 개인은 소유자 본인에게만(타인 마스킹). */
class AccountMaskingTest {

    private static final Long VIEWER = 1L;

    @Test
    void 공용계좌는_누구에게나_표시() {
        Account family = new Account(1L, "공용통장", AssetType.BANK, AccountOwnerType.FAMILY, null, Visibility.FAMILY);
        AccountRef ref = AccountMasking.visibleRef(family, VIEWER);
        assertThat(ref).isNotNull();
        assertThat(ref.name()).isEqualTo("공용통장");
    }

    @Test
    void 본인_개인계좌는_표시() {
        Account mine = new Account(1L, "내카드", AssetType.BANK, AccountOwnerType.PERSON, VIEWER, Visibility.PRIVATE);
        assertThat(AccountMasking.visibleRef(mine, VIEWER)).isNotNull();
    }

    @Test
    void 타인_개인계좌는_마스킹된다() {
        Account others = new Account(1L, "배우자카드", AssetType.BANK, AccountOwnerType.PERSON, 2L, Visibility.PRIVATE);
        assertThat(AccountMasking.visibleRef(others, VIEWER)).isNull();
    }

    @Test
    void 계좌_미참조는_null() {
        assertThat(AccountMasking.visibleRef(null, VIEWER)).isNull();
    }
}
