package com.familyos.common.domain;

/**
 * 가족 내 역할.
 *
 * <p>도메인(person)에 속하는 enum이지만, 인증 주체({@code AuthUser})와 권한 판정
 * ({@code VisibilityGuard})이 공통으로 의존하므로 common 에 둔다. 도메인 패키지들은
 * 여기서 import 한다. (TransactionType 등 도메인 전용 enum 은 각 도메인에 둔다.)
 */
public enum FamilyRole {
    PARENT,
    CHILD
}
