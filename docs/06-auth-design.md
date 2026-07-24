# 가족 생활기록 시스템 — 인증·인가 상세설계 (v1)

> 기준: API명세 v1, JPA Entity v1
> 스택: **Spring Boot 4.1 + Spring Security 7 + Java 21**
> 범위: JWT 구조, Security 필터 체인, 멀티테넌트(family) 주입, visibility 권한판정, RefreshToken 회전

---

## 0. Spring Security 7 주의점 (Boot 4 기준)

Boot 4 / Security 7에서 바뀐 것 중 본 설계에 영향:

| 변경 | 대응 |
|------|------|
| **CSRF가 API에도 기본 활성화** | JWT + Stateless라 쿠키 세션 미사용 → **CSRF 비활성**(`csrf.disable()`). 단, 토큰을 쿠키에 저장하면 CSRF 필요 — 본 설계는 **Authorization 헤더** 방식이라 비활성 안전 |
| `authorizeRequests()` 제거 | **`authorizeHttpRequests()`** 만 사용 |
| `WebSecurityConfigurerAdapter` 제거 | `SecurityFilterChain` **빈** 방식 (람다 DSL) |
| 세션 | `SessionCreationPolicy.STATELESS` (JWT라 세션 미사용) |

> 토큰 전달 — **플랫폼별 분리** (리뷰 반영):
> - **모바일 앱(RN/Flutter)**: Authorization: Bearer 헤더 + expo-secure-store 저장. CSRF 무관
> - **웹**: 두 옵션 중 택1 —
>   (A) 헤더 + 메모리 보관: 단순하나 **새로고침 시 로그아웃**(refresh로 복구)
>   (B) httpOnly 쿠키(refresh) + CSRF 토큰: 새로고침 유지, 단 CSRF 보호 필요(쿠키 경로만 활성)
> - MVP 권장: 앱=헤더, 웹=(A) 메모리+자동 refresh로 단순 출발. 웹 UX 불만 시 (B)로 전환
> - 즉 "CSRF 비활성"은 **헤더 경로에 한함**. 웹 쿠키 도입 시 해당 경로만 CSRF 활성

---

## 1. JWT 구조

### 1-1. Access Token (단기, 30분)
헤더: `{ "alg": "HS256", "typ": "JWT" }` (대칭키 HS256 — 단일 서버라 충분. 멀티 서비스 분리 시 RS256 고려)

페이로드(클레임):
```json
{
  "sub": "1",            // personId
  "accountId": 10,       // userAccountId
  "familyId": 1,         // 로그인 시 결정 — 멀티테넌트 핵심
  "role": "PARENT",      // PARENT / CHILD
  "type": "ACCESS",
  "iat": 1718780000,
  "exp": 1718781800      // 30분
}
```

### 1-2. Refresh Token (장기, 14일)
```json
{ "sub": "1", "accountId": 10, "type": "REFRESH",
  "jti": "uuid-...",     // 토큰 고유 ID (회전·폐기 추적)
  "iat": ..., "exp": ... }
```
- familyId/role은 Refresh에 **불포함** (재발급 시 DB에서 최신값 조회 — 역할 변경 즉시 반영)

### 1-3. 키 관리
- 비밀키는 환경변수/설정 외부화 (`JWT_SECRET`) — 코드 하드코딩 금지 (Cloud-Portable 원칙)
- Access/Refresh 서로 다른 시크릿 권장

---

## 2. Security 필터 체인

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize 등 메서드 보안
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                 // 헤더 기반 JWT → CSRF 불필요
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth           // Security 7: authorizeHttpRequests
                .requestMatchers("/api/v1/auth/login",
                                 "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(restAuthEntryPoint)   // 401 JSON
                .accessDeniedHandler(restAccessDeniedHandler)); // 403 JSON
        return http.build();
    }
}
```

### 필터 처리 순서
1. **JwtAuthenticationFilter** (커스텀) — 헤더에서 토큰 추출·검증 → 인증 컨텍스트 설정
2. 인증 성공 시 `FamilyContext`에 familyId 주입 (3장)
3. authorizeHttpRequests — 경로별 인증 요구
4. (컨트롤러 진입) 메서드 보안 + 서비스 계층 visibility 판정

---

## 3. 멀티테넌트 (family) 주입 흐름 — 핵심

토큰의 familyId를 요청 단위로 전파하고, JPA 필터·MyBatis에 일관 적용.

### 3-1. JwtAuthenticationFilter
```
1) Authorization 헤더에서 Bearer 토큰 추출
2) 서명·만료 검증 (실패 → 401)
3) 클레임에서 personId, familyId, role 추출
4) Authentication 객체 생성 (principal = AuthUser{personId, accountId, familyId, role})
   SecurityContextHolder에 저장
5) FamilyContext(요청 스코프 빈/ThreadLocal)에 familyId 저장
```

### 3-2. JPA 멀티테넌트 필터 적용
요청 처리 시작 시 Hibernate 필터 활성화 (인터셉터 또는 OncePerRequestFilter 또는 AOP):
```java
session.enableFilter("familyFilter")
       .setParameter("familyId", FamilyContext.getFamilyId());
```
→ FamilyScopedEntity 상속 엔티티 조회는 자동으로 family_id 한정

### 3-3. MyBatis 멀티테넌트 (★ 자동 안 됨)
- Hibernate 필터는 MyBatis에 적용 안 됨
- **MyBatis 매퍼는 `familyId`를 명시 파라미터로 받아 WHERE에 직접** 사용
- 공통 처리: MyBatis Interceptor로 `familyId` 자동 주입 또는 매퍼 규칙으로 강제
- soft-delete도 동일 — `deleted_at IS NULL` 명시

### 3-4. 미래(B, 여러 가족 소속)
- 로그인 시 멤버십 1개면 자동, 여러 개면 가족 선택 → 선택된 familyId로 토큰 발급
- 가족 전환: `POST /api/v1/auth/switch-family { familyId }` → 멤버십 검증 후 새 토큰

---

## 4. Visibility 권한 판정 — 도메인 공통

### 4-1. 판정 규칙 (재확인)

| visibility | 조회 가능자 |
|------------|------------|
| PRIVATE | author(created_by) 본인만 |
| SHARED_PERSONAL | author + subject 지정자 (일정·일상기록) |
| PARENTS | 같은 family의 role=PARENT 전원 |
| FAMILY | 같은 family 전원 |

### 4-2. 적용 지점 — 2단계 방어

**① 목록 조회 (쿼리 레벨 필터)**
- 성능·보안상 DB 쿼리에서 visibility 조건을 WHERE로 적용 (앱 메모리 필터링 금지)
- 예(개념): `WHERE family_id=:fid AND (visibility='FAMILY'
    OR (visibility='PARENTS' AND :role='PARENT')
    OR (visibility='PRIVATE' AND created_by=:pid)
    OR (visibility='SHARED_PERSONAL' AND (created_by=:pid OR EXISTS(subject))))`
- 복잡 → 타임라인·목록은 MyBatis로 명시 구현 (JPA Specification도 가능)

**② 단건 조회/수정 (서비스 레벨 가드)**
```java
VisibilityGuard.assertCanView(resource, authUser);   // 위반 → 404(숨김) 또는 403
VisibilityGuard.assertCanEdit(resource, authUser);    // 보통 author만, PARENT 관리권
```

### 4-3. 수정/삭제 권한
| 동작 | 기본 규칙 |
|------|-----------|
| 수정 | author 본인. (PARENT는 가족공동 자원 관리 가능 — 도메인별 정책) |
| 삭제 | author 본인 (soft delete). deleted_by 기록 |
| 자녀(CHILD) 자원 | 부모가 대신 생성·관리 (author=부모) |

### 4-3-1. 참조 대상 alive 검증 (★ FK가 못 막는 부분)
- **FK는 물리 행 존재만 보장, alive(deleted_at IS NULL)는 모름** → soft-delete된 부모를 신규 자식이 참조 가능
- 신규 생성/수정 시 참조 대상이 **alive인지 서비스에서 검증**(위반 422):
  - Transaction → category_id, source/target_account_id, subject_person_id, collection_id
  - Diary/Schedule → subject person, collection_id
  - *Tag 연결 → tag_id
- 삭제된 대상은 신규 입력 선택지에서 제외(UI) + 서버 재검증(2중)
- 과거 데이터의 삭제된 참조는 유지(이력 보존), "(삭제됨)" 표기 — account 규칙(05-2-1)과 동일

### 4-4. SHARED_PERSONAL 표시 수준
- 타인의 SHARED_PERSONAL 일정 조회 시, 뷰어의 PersonSetting.sharedScheduleDetailLevel 확인
- SUMMARY면 응답에서 title 일부·상세 마스킹(시간·바쁨만), FULL이면 전체

---

## 5. RefreshToken 회전 (보안)

### 5-1. 저장
- RefreshToken은 서버 저장 (테이블 또는 Redis). 본 설계: **DB 테이블 `refresh_token`**
  `{ id, account_id, jti, expires_at, revoked, created_at }`
- **BaseEntity 비상속**: soft-delete 대상 아님. 폐기는 `revoked` 플래그(물리 관리). family_id 없음(account_id 기반)
- (단일 서버·소규모라 DB로 충분. 확장 시 Redis)

### 5-2. 회전(Rotation) 흐름
```
1) /auth/refresh 요청 (RefreshToken 제출)
2) 서명·만료·revoked 검증
3) jti가 DB에 유효한지 확인
4) 유효 → 기존 jti revoked 처리 + 새 Access·Refresh 발급(새 jti 저장)
5) 재사용 감지: 이미 revoked된 jti 재제출 시 → 해당 계정 전체 토큰 폐기(탈취 의심)

> **동시 요청 grace window** (프론트 07-4-4 동시 401 큐잉과 정합):
> 정상적인 동시 요청이 같은 refresh를 거의 동시에 보낼 수 있다. revoked 직후 **짧은 유예(예: 5~10초)**
> 내 같은 jti 재제출은 탈취가 아닌 레이스로 간주해, 직전 회전으로 발급된 최신 토큰을 반환(또는 무시).
> 유예 밖의 revoked jti 재사용만 탈취로 판정해 전체 폐기. 프론트는 refresh 1회 큐잉으로 레이스 자체를 최소화.
```

### 5-3. 로그아웃
- 제출된 RefreshToken의 jti를 revoked 처리
- Access는 단기라 자연 만료 대기(또는 블랙리스트 — MVP는 미사용, 만료 의존)

---

## 6. 비밀번호 / 계정

- 해시: **BCrypt** (Spring Security `PasswordEncoder`, strength 10~12)
- 로그인 실패 처리: 횟수 제한(선택, MVP 후순위)
- 자녀 계정 생성: PARENT가 `POST /family/members/{personId}/account` — 기존 Person에 UserAccount 연결
- UserAccount.login_id UNIQUE는 **생성컬럼 alive_uk 방식** (재가입 허용, 03-1-4 참조)
- 본인 비밀번호 변경: `PUT /auth/password`(2026-07 리뉴얼 추가) — 현재 비밀번호 검증 후 변경, 성공 시 `revokeAllByAccountId`로 해당 계정의 모든 refresh token 폐기(현재 기기 포함 재로그인 필요). `UserAccount.changePassword()`는 원래부터 있었으나 미배선 상태였음.

### 6-1. PersonSetting 생성 시점 (확정)
- 별도 생성 단계 없음 — **lazy**: `GET /me/settings`는 행이 없으면 **기본값 응답**
  (sharedScheduleDetailLevel=FULL). 404 아님
- `PUT /me/settings` 최초 호출 시 행 생성(upsert). 이후 갱신
- 계정 생성 시 미리 만들지 않아 단순

### 6-2. Hermes(텔레그램) 내부 API 인증 (확정)
- Hermes는 외부 사용자 토큰이 아닌 **전용 서비스 토큰**으로 인증 (별도 시크릿)
- 내부 엔드포인트(`/integrations/telegram/*`)는 **공개 webhook과 경로·인증 분리**:
  - 구글 webhook: 인증 예외(공개) + 채널 토큰 검증
  - Hermes 내부 API: 서비스 토큰 필수 + 가능하면 네트워크 레벨 제한(내부망/방화벽)
- Hermes는 telegram_user_id → Person 매핑으로 행동 주체 결정, family/visibility 규칙 동일 적용

---

## 7. CORS
- 웹(브라우저) + 모바일 앱. 웹 도메인만 허용 오리진 등록
- 모바일 앱은 네이티브라 CORS 미해당(서버-서버성)
- 허용 헤더: Authorization, Content-Type. 허용 메서드: GET/POST/PUT/PATCH/DELETE

---

## 8. 열린 항목
1. Access 블랙리스트 도입 여부 (강제 즉시 로그아웃 필요 시) — MVP는 만료 의존
2. 로그인 실패 잠금·2FA — 외부 오픈(B) 시점 강화
3. MyBatis family_id 자동주입 Interceptor 구현 상세
4. 키 회전(시크릿 rotation) 정책 — 운영 단계

---

## 9. 다음 단계
**백엔드 레이어/패키지 구조 설계** (Gradle 모듈, Controller/Service/Repository, 공통 예외·응답, FamilyContext·VisibilityGuard 위치) → 프론트 기술결정·화면정의
