package com.familyos.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실 MySQL(Testcontainers) 기반 통합테스트 베이스.
 *
 * <p>Docker 필요. {@code @Tag("integration")} 로 기본 test 에서 제외되며,
 * {@code ./gradlew integrationTest} (Docker PC) 로 실행한다.
 *
 * <p>{@code @ServiceConnection} 이 컨테이너 접속정보로 datasource 를 자동 구성 →
 * Flyway(V1__init.sql)가 21테이블을 만들고 ddl-auto:validate 가 엔티티와 대조한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("family_os");
}
