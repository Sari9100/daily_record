package com.familyos.ledger.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.ledger.dto.AccountRequest;
import com.familyos.ledger.dto.AccountResponse;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.AccountOwnerType;
import com.familyos.ledger.repository.AccountRepository;
import com.familyos.person.repository.FamilyMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 계좌 관리.
 *
 * <p>visibility 는 PRIVATE/PARENTS/FAMILY 만(가계부는 SHARED_PERSONAL 미사용) — 위반 422.
 * 개인 계좌(PERSON)는 owner_person_id 필수+가족 구성원(alive) 검증, 공용 계좌(FAMILY)는 owner_person_id NULL.
 * 단건 수정/삭제는 작성자 본인만(VisibilityGuard). 삭제는 soft-delete(과거 거래 FK 유지).
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final VisibilityGuard visibilityGuard;
    private final SoftDeleteSupport softDeleteSupport;

    public AccountService(AccountRepository accountRepository,
                          FamilyMembershipRepository membershipRepository,
                          VisibilityGuard visibilityGuard,
                          SoftDeleteSupport softDeleteSupport) {
        this.accountRepository = accountRepository;
        this.membershipRepository = membershipRepository;
        this.visibilityGuard = visibilityGuard;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<AccountResponse> list() {
        AuthUser user = FamilyContext.require();
        return accountRepository.findVisible(user.familyId(), user.personId(), user.isParent())
                .stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse create(AccountRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        validateVisibility(req.visibility());
        Long ownerPersonId = resolveOwner(familyId, req.ownerType(), req.ownerPersonId());

        Account account = accountRepository.save(new Account(
                familyId, req.name(), req.assetType(), req.ownerType(), ownerPersonId, req.visibility()));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest req) {
        AuthUser user = FamilyContext.require();
        Account account = load(id, user.familyId());
        visibilityGuard.assertCanEdit(account, user); // 작성자 본인만 (위반 404)

        validateVisibility(req.visibility());
        Long ownerPersonId = resolveOwner(user.familyId(), req.ownerType(), req.ownerPersonId());
        account.update(req.name(), req.assetType(), req.ownerType(), ownerPersonId, req.visibility());
        return AccountResponse.from(account);
    }

    @Transactional
    public void delete(Long id) {
        AuthUser user = FamilyContext.require();
        Account account = load(id, user.familyId());
        visibilityGuard.assertCanEdit(account, user);
        softDeleteSupport.softDelete(account, accountRepository); // deleted_by set + soft-delete
    }

    // ---- 내부 ----

    private Account load(Long id, Long familyId) {
        return accountRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("계좌", id));
    }

    private void validateVisibility(Visibility visibility) {
        if (visibility == Visibility.SHARED_PERSONAL) {
            throw new BusinessException("가계부 계좌는 SHARED_PERSONAL 을 사용할 수 없습니다.");
        }
    }

    /** ownerType 규칙 + 개인 계좌 소유자 alive(가족 구성원) 검증. */
    private Long resolveOwner(Long familyId, AccountOwnerType ownerType, Long ownerPersonId) {
        if (ownerType == AccountOwnerType.FAMILY) {
            return null; // 공용 계좌는 소유자 없음
        }
        // PERSON
        if (ownerPersonId == null) {
            throw new BusinessException("개인 계좌는 ownerPersonId 가 필요합니다.");
        }
        if (!membershipRepository.existsByFamily_IdAndPerson_Id(familyId, ownerPersonId)) {
            throw new BusinessException("소유자는 현재 가족의 구성원(유효)이어야 합니다.");
        }
        return ownerPersonId;
    }
}
