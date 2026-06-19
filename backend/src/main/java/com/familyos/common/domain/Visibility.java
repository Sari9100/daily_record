package com.familyos.common.domain;

/**
 * 자원 공개 범위. 권한 판정의 핵심.
 *
 * <p>{@code VisibilityGuard}(common)가 공통으로 의존하므로 common 에 둔다.
 *
 * <ul>
 *   <li>{@link #PRIVATE} — author(created_by) 본인만</li>
 *   <li>{@link #SHARED_PERSONAL} — author + subject 지정자 (일정·일상기록)</li>
 *   <li>{@link #PARENTS} — 같은 family 의 role=PARENT 전원</li>
 *   <li>{@link #FAMILY} — 같은 family 전원</li>
 * </ul>
 *
 * 가계부(Transaction/Account)는 PRIVATE/PARENTS/FAMILY 만 사용(SHARED_PERSONAL 미사용).
 * 해당 제약 검증은 각 도메인 서비스 계층에서 한다.
 */
public enum Visibility {
    PRIVATE,
    SHARED_PERSONAL,
    PARENTS,
    FAMILY
}
