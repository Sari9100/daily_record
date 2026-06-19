package com.familyos.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실 MySQL(Testcontainers) 기반 통합테스트 베이스.
 *
 * <p>Docker 필요. {@code @Tag("integration")} 로 기본 test 에서 제외되며,
 * {@code ./gradlew integrationTest} (Docker PC) 로 실행한다.
 *
 * <p><b>싱글톤 컨테이너 패턴</b>: static 블록에서 1회 기동하고 JVM 종료까지 유지(@Testcontainers 생명주기 미사용).
 * 여러 테스트 클래스가 컨텍스트 캐시를 공유해도 컨테이너가 멈추지 않아 연결이 끊기지 않는다.
 * {@code @DynamicPropertySource} 로 datasource 를 컨테이너에 연결 → Flyway(V1)가 21테이블 생성, ddl-auto:validate 대조.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("family_os")
                    .withUrlParam("serverTimezone", "UTC")
                    .withUrlParam("useSSL", "false")
                    .withUrlParam("allowPublicKeyRetrieval", "true");

    static {
        MYSQL.start(); // 1회 기동, 공유. Ryuk 가 JVM 종료 시 정리
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
