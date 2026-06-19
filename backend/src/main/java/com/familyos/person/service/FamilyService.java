package com.familyos.person.service;

import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.ErrorCode;
import com.familyos.common.error.NotFoundException;
import com.familyos.person.dto.AccountCreatedResponse;
import com.familyos.person.dto.AddMemberRequest;
import com.familyos.person.dto.CreateAccountRequest;
import com.familyos.person.dto.FamilyOverviewResponse;
import com.familyos.person.dto.MemberCreatedResponse;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.entity.UserAccount;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.person.repository.FamilyRepository;
import com.familyos.person.repository.PersonRepository;
import com.familyos.person.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 가족·구성원·계정 관리.
 *
 * <p>Family/Person/Membership/UserAccount 는 BaseEntity(가족 필터 미적용)이므로,
 * 모든 조회·검증에서 {@code FamilyContext.getFamilyId()} 로 격리를 직접 건다.
 */
@Service
@Transactional(readOnly = true)
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final PersonRepository personRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public FamilyService(FamilyRepository familyRepository,
                         PersonRepository personRepository,
                         FamilyMembershipRepository membershipRepository,
                         UserAccountRepository userAccountRepository,
                         PasswordEncoder passwordEncoder) {
        this.familyRepository = familyRepository;
        this.personRepository = personRepository;
        this.membershipRepository = membershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 현재 가족 개요(구성원 + 계정 보유 여부). */
    public FamilyOverviewResponse getCurrentFamily() {
        Long familyId = FamilyContext.getFamilyId();
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> NotFoundException.of("가족", familyId));

        List<FamilyMembership> memberships = membershipRepository.findAliveByFamilyIdFetchPerson(familyId);

        Set<Long> personIdsWithAccount = accountHoldingPersonIds(
                memberships.stream().map(m -> m.getPerson().getId()).toList());

        List<FamilyOverviewResponse.MemberDto> members = memberships.stream()
                .map(m -> new FamilyOverviewResponse.MemberDto(
                        m.getPerson().getId(),
                        m.getPerson().getName(),
                        m.getRole(),
                        personIdsWithAccount.contains(m.getPerson().getId())))
                .toList();

        return new FamilyOverviewResponse(
                new FamilyOverviewResponse.FamilyDto(family.getId(), family.getName()),
                members);
    }

    /** 구성원 추가 (계정 없음). PARENT 전용 — 컨트롤러 @PreAuthorize 로 강제. */
    @Transactional
    public MemberCreatedResponse addMember(AddMemberRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> NotFoundException.of("가족", familyId));

        Person person = personRepository.save(
                new Person(req.name(), req.birthDate(), req.timezone()));

        membershipRepository.save(
                new FamilyMembership(family, person, req.role(), Instant.now()));

        return new MemberCreatedResponse(person.getId(), person.getName(), req.role(), false);
    }

    /** 기존 구성원에게 로그인 계정 부여 (자녀 성장 등). PARENT 전용. */
    @Transactional
    public AccountCreatedResponse createAccountForMember(Long personId, CreateAccountRequest req) {
        Long familyId = FamilyContext.getFamilyId();

        // 대상 person 이 현재 가족의 alive 구성원인지 검증 (타 가족 personId 는 404 로 숨김)
        FamilyMembership membership = membershipRepository
                .findByFamily_IdAndPerson_Id(familyId, personId)
                .orElseThrow(() -> NotFoundException.of("구성원", personId));
        Person person = membership.getPerson();

        // 이미 계정을 가진 경우 → 409
        if (userAccountRepository.existsByPerson_Id(personId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 계정을 가진 구성원입니다.");
        }
        // login_id 중복 → 409
        if (userAccountRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 로그인 ID 입니다.");
        }

        UserAccount account = userAccountRepository.save(
                new UserAccount(person, req.loginId(), passwordEncoder.encode(req.password())));

        return new AccountCreatedResponse(account.getId(), personId, account.getLoginId());
    }

    private Set<Long> accountHoldingPersonIds(List<Long> personIds) {
        if (personIds.isEmpty()) {
            return Set.of();
        }
        return userAccountRepository.findByPerson_IdIn(personIds).stream()
                .map(a -> a.getPerson().getId())
                .collect(Collectors.toSet());
    }
}
