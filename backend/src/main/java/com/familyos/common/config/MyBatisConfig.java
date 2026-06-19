package com.familyos.common.config;

import com.familyos.common.tenant.MyBatisTenantInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 공통 설정.
 *
 * <p>{@link MyBatisTenantInterceptor} 를 {@link Interceptor} 빈으로 등록하면
 * Spring Boot 의 MyBatis 오토컨피그가 자동으로 SqlSessionFactory 에 플러그인한다.
 */
@Configuration
public class MyBatisConfig {

    @Bean
    Interceptor myBatisTenantInterceptor() {
        return new MyBatisTenantInterceptor();
    }
}
