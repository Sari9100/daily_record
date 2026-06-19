package com.familyos.person.repository;

import com.familyos.person.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @SoftDelete 가 deleted_at IS NULL 을 자동 적용하므로, 아래 조회는 모두 alive 계정만 대상.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 로그인용. alive 계정만. */
    Optional<UserAccount> findByLoginId(String loginId);

    /** 살아있는 login_id 중복 여부 (재가입 허용이므로 alive 기준). */
    boolean existsByLoginId(String loginId);

    /** 해당 Person 이 이미 alive 계정을 가졌는지. */
    boolean existsByPerson_Id(Long personId);

    /** 구성원 목록의 계정 보유 여부 일괄 조회용. */
    List<UserAccount> findByPerson_IdIn(Collection<Long> personIds);
}
