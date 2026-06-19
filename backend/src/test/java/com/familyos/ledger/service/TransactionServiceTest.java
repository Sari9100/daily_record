package com.familyos.ledger.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.dto.TransactionResponse;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.AccountOwnerType;
import com.familyos.ledger.entity.AssetType;
import com.familyos.ledger.entity.Category;
import com.familyos.ledger.entity.CategoryType;
import com.familyos.ledger.entity.SettlementStatus;
import com.familyos.ledger.entity.Transaction;
import com.familyos.ledger.entity.TransactionType;
import com.familyos.ledger.repository.AccountRepository;
import com.familyos.ledger.repository.CategoryRepository;
import com.familyos.ledger.repository.TransactionRepository;
import com.familyos.person.repository.FamilyMembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * TransactionService 핵심 규칙(필수): 거래유형-계좌 / visibility / alive 검증 / settlement 서버결정.
 * 빌드/실행은 이 환경에서 미검증.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long FAMILY_ID = 1L;

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock com.familyos.collection.repository.CollectionRepository collectionRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock VisibilityGuard visibilityGuard;
    @Mock com.familyos.common.audit.SoftDeleteSupport softDeleteSupport;

    TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, accountRepository, categoryRepository,
                collectionRepository, membershipRepository, visibilityGuard, softDeleteSupport);
        FamilyContext.set(new AuthUser(1L, 10L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private TransactionRequest req(TransactionType type, Visibility vis,
                                   Long source, Long target, Long categoryId) {
        return new TransactionRequest(type, new BigDecimal("50000"), "KRW",
                source, target, categoryId, null, vis, Instant.parse("2026-06-19T08:30:00Z"), "메모", null, null);
    }

    @Test
    void EXPENSE_는_출금계좌가_없으면_422() {
        assertThatThrownBy(() -> service.create(req(TransactionType.EXPENSE, Visibility.PRIVATE, null, null, null)))
                .isInstanceOf(BusinessException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void EXPENSE_에_입금계좌가_있으면_422() {
        lenient().when(accountRepository.findByIdAndFamilyId(any(), any())).thenReturn(Optional.of(account(AccountOwnerType.FAMILY, null)));
        assertThatThrownBy(() -> service.create(req(TransactionType.EXPENSE, Visibility.PRIVATE, 5L, 6L, null)))
                .isInstanceOf(BusinessException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void INCOME_는_입금계좌가_없으면_422() {
        assertThatThrownBy(() -> service.create(req(TransactionType.INCOME, Visibility.PRIVATE, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void TRANSFER_는_출금입금이_같으면_422() {
        assertThatThrownBy(() -> service.create(req(TransactionType.TRANSFER, Visibility.PRIVATE, 5L, 5L, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 가계부는_SHARED_PERSONAL_금지_422() {
        assertThatThrownBy(() -> service.create(req(TransactionType.EXPENSE, Visibility.SHARED_PERSONAL, 5L, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 삭제되었거나_타가족_계좌_참조시_422() {
        when(accountRepository.findByIdAndFamilyId(5L, FAMILY_ID)).thenReturn(Optional.empty()); // alive 아님
        assertThatThrownBy(() -> service.create(req(TransactionType.EXPENSE, Visibility.PRIVATE, 5L, null, null)))
                .isInstanceOf(BusinessException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void 정상_EXPENSE_생성시_settlement_는_항상_NONE() {
        when(accountRepository.findByIdAndFamilyId(5L, FAMILY_ID)).thenReturn(Optional.of(account(AccountOwnerType.PERSON, 1L)));
        when(categoryRepository.findByIdAndFamilyId(3L, FAMILY_ID)).thenReturn(Optional.of(
                new Category(FAMILY_ID, "식비", CategoryType.EXPENSE, null, false)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // 공동(PARENTS)이라도 MVP 규칙상 NONE 이어야 한다
        TransactionResponse res = service.create(req(TransactionType.EXPENSE, Visibility.PARENTS, 5L, null, 3L));

        assertThat(res.settlementStatus()).isEqualTo(SettlementStatus.NONE);
        assertThat(res.visibility()).isEqualTo(Visibility.PARENTS);
    }

    private Account account(AccountOwnerType ownerType, Long ownerPersonId) {
        return new Account(FAMILY_ID, "계좌", AssetType.BANK, ownerType, ownerPersonId, Visibility.PRIVATE);
    }
}
