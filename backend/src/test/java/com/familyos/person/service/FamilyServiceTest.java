package com.familyos.person.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.ErrorCode;
import com.familyos.common.error.NotFoundException;
import com.familyos.person.dto.CreateAccountRequest;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.entity.UserAccount;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.person.repository.FamilyRepository;
import com.familyos.person.repository.PersonRepository;
import com.familyos.person.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FamilyService 검증: family 격리(필수) + 계정 생성 규칙.
 *
 * <p>주의: 이 환경에서는 빌드/실행 미검증. 의도와 규칙을 코드로 고정하는 용도.
 */
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    private static final Long FAMILY_ID = 1L;
    private static final Long OTHER_PERSON_ID = 99L;

    @Mock FamilyRepository familyRepository;
    @Mock PersonRepository personRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;

    FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = new FamilyService(familyRepository, personRepository,
                membershipRepository, userAccountRepository, passwordEncoder);
        // 현재 가족=1, 부모로 로그인한 상태를 가정
        FamilyContext.set(new AuthUser(1L, 10L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    @Test
    void 타가족_personId_로_계정생성_시도하면_404로_숨긴다() {
        // 현재 가족(1)에 OTHER_PERSON_ID 멤버십이 없음 → 타 가족 또는 미존재
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, OTHER_PERSON_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                familyService.createAccountForMember(OTHER_PERSON_ID, req("child01", "password123")))
                .isInstanceOf(NotFoundException.class);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void 이미_계정이_있으면_409() {
        Person person = new Person("첫째", null, null);
        FamilyMembership membership = new FamilyMembership(
                new Family("우리집"), person, FamilyRole.CHILD, Instant.now());
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, 2L))
                .thenReturn(Optional.of(membership));
        when(userAccountRepository.existsByPerson_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> familyService.createAccountForMember(2L, req("child01", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void loginId_중복이면_409() {
        Person person = new Person("첫째", null, null);
        FamilyMembership membership = new FamilyMembership(
                new Family("우리집"), person, FamilyRole.CHILD, Instant.now());
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, 2L))
                .thenReturn(Optional.of(membership));
        when(userAccountRepository.existsByPerson_Id(2L)).thenReturn(false);
        when(userAccountRepository.existsByLoginId("taken")).thenReturn(true);

        assertThatThrownBy(() -> familyService.createAccountForMember(2L, req("taken", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void 정상_생성_시_비밀번호를_해시하여_저장한다() {
        Person person = new Person("첫째", null, null);
        FamilyMembership membership = new FamilyMembership(
                new Family("우리집"), person, FamilyRole.CHILD, Instant.now());
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, 2L))
                .thenReturn(Optional.of(membership));
        when(userAccountRepository.existsByPerson_Id(2L)).thenReturn(false);
        when(userAccountRepository.existsByLoginId("child01")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        familyService.createAccountForMember(2L, req("child01", "password123"));

        verify(passwordEncoder).encode("password123");
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    private CreateAccountRequest req(String loginId, String password) {
        return new CreateAccountRequest(loginId, password);
    }
}
