package com.familyos.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 트랜잭션 어드바이저를 최외곽(HIGHEST_PRECEDENCE)에 둔다.
 *
 * <p>{@code FamilyFilterAspect}(LOWEST_PRECEDENCE)가 트랜잭션 안쪽에서 실행되도록 보장하기 위함.
 * 트랜잭션이 먼저 시작되어 Hibernate 세션이 열린 뒤, 그 세션에 familyFilter 를 활성화해야 한다.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class TransactionOrderConfig {
}
