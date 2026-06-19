package com.familyos.person.bootstrap;

import com.familyos.common.domain.FamilyRole;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.entity.UserAccount;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.person.repository.FamilyRepository;
import com.familyos.person.repository.PersonRepository;
import com.familyos.person.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 최초 1회 부트스트랩 — 멱등.
 *
 * <p>기동 시 Family 가 하나도 없으면 .env 자격증명으로 가족 1개 + 부모 Person + 부모 역할 멤버십 +
 * 로그인 계정을 생성한다. 이후 Family 가 존재하면 아무 것도 하지 않는다(멱등).
 *
 * <p>나머지 구성원(배우자·자녀)은 {@code POST /api/v1/family/members} 로 추가한다.
 *
 * <p>안전장치:
 * <ul>
 *   <li>비활성({@code app.bootstrap.enabled=false}) → 생략</li>
 *   <li>이미 부트스트랩됨(Family 존재) → 생략</li>
 *   <li>자격증명 미설정 → 경고 로그 후 생략(앱 기동은 막지 않음)</li>
 * </ul>
 * 시딩 시 인증 주체가 없으므로 created_by/updated_by 는 NULL(DDL 허용).
 */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final BootstrapProperties props;
    private final FamilyRepository familyRepository;
    private final PersonRepository personRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapRunner(BootstrapProperties props,
                           FamilyRepository familyRepository,
                           PersonRepository personRepository,
                           FamilyMembershipRepository membershipRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this.props = props;
        this.familyRepository = familyRepository;
        this.personRepository = personRepository;
        this.membershipRepository = membershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            log.info("부트스트랩 비활성화(app.bootstrap.enabled=false) — 생략");
            return;
        }
        if (familyRepository.count() > 0) {
            log.debug("이미 부트스트랩됨(Family 존재) — 생략");
            return;
        }
        if (!props.hasCredentials()) {
            log.warn("부트스트랩 자격증명(BOOTSTRAP_LOGIN_ID/BOOTSTRAP_PASSWORD) 미설정 — 초기 계정 시드를 생략합니다. "
                    + "환경변수 설정 후 재기동하면 최초 가족·부모 계정이 생성됩니다.");
            return;
        }

        Family family = familyRepository.save(new Family(props.familyName()));
        Person parent = personRepository.save(new Person(props.parentName(), null, props.timezone()));
        membershipRepository.save(new FamilyMembership(family, parent, FamilyRole.PARENT, Instant.now()));
        userAccountRepository.save(
                new UserAccount(parent, props.loginId(), passwordEncoder.encode(props.password())));

        // loginId 는 비밀이 아니므로 로그 가능. 비밀번호/해시는 절대 출력하지 않는다.
        log.info("부트스트랩 완료 — familyId={}, parentPersonId={}, loginId={}",
                family.getId(), parent.getId(), props.loginId());
    }
}
