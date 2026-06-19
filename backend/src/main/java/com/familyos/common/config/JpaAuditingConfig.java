package com.familyos.common.config;

import com.familyos.common.audit.AuditorAwareImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA Auditing 활성화.
 * - createdBy/updatedBy = 현재 로그인 Person.id ({@link AuditorAwareImpl})
 * - createdAt/updatedAt = UTC Instant
 *
 * 별도 @Configuration 으로 분리하여 @DataJpaTest 등에서 선택적 로딩 가능하게 한다.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return new AuditorAwareImpl();
    }

    /** @CreatedDate/@LastModifiedDate 를 UTC Instant 로 채운다. */
    @Bean
    DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
