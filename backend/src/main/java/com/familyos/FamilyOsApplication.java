package com.familyos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * JWT 인증만 사용하므로 Spring Security 기본 인메모리 사용자(생성 비밀번호) 자동구성을 제외한다.
 * (그렇지 않으면 기동 시 'Using generated security password' 경고 + 미사용 inMemoryUserDetailsManager 생성)
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FamilyOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyOsApplication.class, args);
    }
}
