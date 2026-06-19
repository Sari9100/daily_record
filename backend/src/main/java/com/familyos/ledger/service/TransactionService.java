package com.familyos.ledger.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.common.web.PageResponse;
import com.familyos.collection.repository.CollectionRepository;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.dto.TransactionResponse;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.Category;
import com.familyos.ledger.entity.SettlementStatus;
import com.familyos.ledger.entity.Transaction;
import com.familyos.ledger.entity.TransactionSource;
import com.familyos.ledger.entity.TransactionType;
import com.familyos.ledger.repository.AccountRepository;
import com.familyos.ledger.repository.CategoryRepository;
import com.familyos.ledger.repository.TransactionRepository;
import com.familyos.person.repository.FamilyMembershipRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 거래 관리 — 가계부 핵심 규칙 집약.
 *
 * <p>검증 순서(골든 패스): ① 거래유형-계좌 규칙·visibility(422) → ② 참조 alive(422) →
 * ③ VisibilityGuard(수정/삭제, 404) → ④ 서버 결정값(settlement) → ⑤ 저장/soft-delete.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CollectionRepository collectionRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final VisibilityGuard visibilityGuard;
    private final SoftDeleteSupport softDeleteSupport;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              CollectionRepository collectionRepository,
                              FamilyMembershipRepository membershipRepository,
                              VisibilityGuard visibilityGuard,
                              SoftDeleteSupport softDeleteSupport) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.collectionRepository = collectionRepository;
        this.membershipRepository = membershipRepository;
        this.visibilityGuard = visibilityGuard;
        this.softDeleteSupport = softDeleteSupport;
    }

    public PageResponse<TransactionResponse> list(@Nullable Instant from, @Nullable Instant to,
                                                  @Nullable TransactionType type, @Nullable Long categoryId,
                                                  @Nullable String scope, int page, int size) {
        AuthUser user = FamilyContext.require();
        Visibility scopeVisibility = parseScope(scope);
        Pageable pageable = PageRequest.of(page, size);

        Page<Transaction> result = transactionRepository.search(
                user.familyId(), user.personId(), user.isParent(),
                from, to, type, categoryId, scopeVisibility, pageable);

        Map<Long, Account> accounts = loadAccounts(user.familyId(), result.getContent());
        Map<Long, Category> categories = loadCategories(user.familyId(), result.getContent());

        Page<TransactionResponse> mapped = result.map(t -> toResponse(t, user, accounts, categories));
        return PageResponse.from(mapped);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();

        // ① 입력 규칙
        validateVisibility(req.visibility());
        validateTypeAccounts(req.transactionType(), req.sourceAccountId(), req.targetAccountId());
        // ② 참조 alive
        validateReferencesAlive(familyId, req);

        // ④ 서버 결정값
        SettlementStatus settlement = decideSettlement(req.visibility());
        String currency = (req.currency() == null || req.currency().isBlank()) ? "KRW" : req.currency();
        TransactionSource source = req.source() == null ? TransactionSource.MANUAL : req.source();

        Transaction tx = transactionRepository.save(new Transaction(
                familyId, req.transactionType(), req.amount(), currency,
                req.sourceAccountId(), req.targetAccountId(), req.categoryId(), req.subjectPersonId(),
                req.visibility(), settlement, req.occurredAt(), req.memo(), req.collectionId(), source));

        return toResponse(tx, user,
                loadAccounts(familyId, List.of(tx)), loadCategories(familyId, List.of(tx)));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Transaction tx = transactionRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("거래", id));

        // ③ 작성자 본인만 수정 (위반 404)
        visibilityGuard.assertCanEdit(tx, user);

        // ① 입력 규칙 + ② 참조 alive
        validateVisibility(req.visibility());
        validateTypeAccounts(req.transactionType(), req.sourceAccountId(), req.targetAccountId());
        validateReferencesAlive(familyId, req);

        SettlementStatus settlement = decideSettlement(req.visibility());
        String currency = (req.currency() == null || req.currency().isBlank()) ? "KRW" : req.currency();

        tx.update(req.transactionType(), req.amount(), currency,
                req.sourceAccountId(), req.targetAccountId(), req.categoryId(), req.subjectPersonId(),
                req.visibility(), settlement, req.occurredAt(), req.memo(), req.collectionId());

        return toResponse(tx, user,
                loadAccounts(familyId, List.of(tx)), loadCategories(familyId, List.of(tx)));
    }

    @Transactional
    public void delete(Long id) {
        AuthUser user = FamilyContext.require();
        Transaction tx = transactionRepository.findByIdAndFamilyId(id, user.familyId())
                .orElseThrow(() -> NotFoundException.of("거래", id));
        visibilityGuard.assertCanEdit(tx, user); // 작성자 본인만
        softDeleteSupport.softDelete(tx, transactionRepository);
    }

    // ---- 규칙 검증 ----

    private void validateVisibility(Visibility visibility) {
        if (visibility == Visibility.SHARED_PERSONAL) {
            throw new BusinessException("가계부 거래는 SHARED_PERSONAL 을 사용할 수 없습니다.");
        }
    }

    /** EXPENSE=source만 / INCOME=target만 / TRANSFER=둘 다(서로 다름). 위반 422. */
    private void validateTypeAccounts(TransactionType type, @Nullable Long source, @Nullable Long target) {
        switch (type) {
            case EXPENSE -> {
                if (source == null) {
                    throw new BusinessException("지출(EXPENSE)은 출금 계좌(sourceAccountId)가 필요합니다.");
                }
                if (target != null) {
                    throw new BusinessException("지출(EXPENSE)은 입금 계좌(targetAccountId)를 가질 수 없습니다.");
                }
            }
            case INCOME -> {
                if (target == null) {
                    throw new BusinessException("수입(INCOME)은 입금 계좌(targetAccountId)가 필요합니다.");
                }
                if (source != null) {
                    throw new BusinessException("수입(INCOME)은 출금 계좌(sourceAccountId)를 가질 수 없습니다.");
                }
            }
            case TRANSFER -> {
                if (source == null || target == null) {
                    throw new BusinessException("이체(TRANSFER)는 출금·입금 계좌가 모두 필요합니다.");
                }
                if (source.equals(target)) {
                    throw new BusinessException("이체(TRANSFER)의 출금·입금 계좌는 서로 달라야 합니다.");
                }
            }
        }
    }

    /** 참조 대상(계좌·카테고리·subject)이 alive(같은 가족)인지 검증. FK 가 못 막는 soft-delete 참조 차단. */
    private void validateReferencesAlive(Long familyId, TransactionRequest req) {
        assertAccountAlive(familyId, req.sourceAccountId());
        assertAccountAlive(familyId, req.targetAccountId());

        if (req.categoryId() != null) {
            Category category = categoryRepository.findByIdAndFamilyId(req.categoryId(), familyId)
                    .orElseThrow(() -> new BusinessException("카테고리가 존재하지 않거나 삭제되었습니다."));
            // INCOME/EXPENSE 는 카테고리 유형이 일치해야 함(통계 일관성)
            boolean typed = req.transactionType() == TransactionType.INCOME
                    || req.transactionType() == TransactionType.EXPENSE;
            if (typed && !category.getType().name().equals(req.transactionType().name())) {
                throw new BusinessException("카테고리 유형이 거래 유형과 일치하지 않습니다.");
            }
        }

        if (req.subjectPersonId() != null
                && !membershipRepository.existsByFamily_IdAndPerson_Id(familyId, req.subjectPersonId())) {
            throw new BusinessException("대상 인물이 현재 가족의 구성원(유효)이 아닙니다.");
        }

        if (req.collectionId() != null
                && collectionRepository.findByIdAndFamilyId(req.collectionId(), familyId).isEmpty()) {
            throw new BusinessException("묶음(collection)이 존재하지 않거나 삭제되었습니다.");
        }
    }

    private void assertAccountAlive(Long familyId, @Nullable Long accountId) {
        if (accountId != null && accountRepository.findByIdAndFamilyId(accountId, familyId).isEmpty()) {
            throw new BusinessException("계좌가 존재하지 않거나 삭제되었습니다: id=" + accountId);
        }
    }

    /**
     * settlement_status 서버 결정. MVP(A): 전부 NONE (사용자 결정, docs/02-214).
     * 확장 B 에서 공동(visibility PARENTS/FAMILY)→PENDING 으로 전환할 단일 지점.
     */
    private SettlementStatus decideSettlement(Visibility visibility) {
        return SettlementStatus.NONE;
    }

    private @Nullable Visibility parseScope(@Nullable String scope) {
        if (scope == null || scope.isBlank() || scope.equalsIgnoreCase("ALL")) {
            return null;
        }
        return switch (scope.toUpperCase()) {
            case "PRIVATE" -> Visibility.PRIVATE;
            case "PARENTS" -> Visibility.PARENTS;
            default -> throw new BusinessException("scope 는 ALL/PRIVATE/PARENTS 만 허용됩니다.");
        };
    }

    // ---- 응답 매핑(마스킹) ----

    private TransactionResponse toResponse(Transaction t, AuthUser viewer,
                                           Map<Long, Account> accounts, Map<Long, Category> categories) {
        return new TransactionResponse(
                t.getId(), t.getTransactionType(), t.getAmount(), t.getCurrency(),
                categoryRef(t.getCategoryId(), categories),
                AccountMasking.visibleRef(account(t.getSourceAccountId(), accounts), viewer.personId()),
                AccountMasking.visibleRef(account(t.getTargetAccountId(), accounts), viewer.personId()),
                t.getSubjectPersonId(), t.getVisibility(), t.getSettlementStatus(),
                t.getOccurredAt(), t.getMemo(), t.getCollectionId(), List.of());
    }

    private @Nullable Account account(@Nullable Long id, Map<Long, Account> accounts) {
        return id == null ? null : accounts.get(id);
    }

    private TransactionResponse.@Nullable CategoryRef categoryRef(@Nullable Long id, Map<Long, Category> categories) {
        if (id == null) {
            return null;
        }
        Category c = categories.get(id);
        return c == null ? null : new TransactionResponse.CategoryRef(c.getId(), c.getName());
    }

    private Map<Long, Account> loadAccounts(Long familyId, Collection<Transaction> txs) {
        List<Long> ids = new ArrayList<>();
        for (Transaction t : txs) {
            if (t.getSourceAccountId() != null) {
                ids.add(t.getSourceAccountId());
            }
            if (t.getTargetAccountId() != null) {
                ids.add(t.getTargetAccountId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findByIdInAndFamilyId(ids, familyId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private Map<Long, Category> loadCategories(Long familyId, Collection<Transaction> txs) {
        List<Long> ids = txs.stream().map(Transaction::getCategoryId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findByIdInAndFamilyId(ids, familyId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }
}
