package com.familyos.common.context;

import com.familyos.common.domain.FamilyRole;

/**
 * 인증 주체(principal). JWT Access 토큰 클레임에서 추출하여 SecurityContext 와 FamilyContext 에 담긴다.
 *
 * @param personId  sub — Person.id (행동 주체)
 * @param accountId UserAccount.id
 * @param familyId  로그인 시 결정된 가족 경계 (멀티테넌트 핵심). 요청으로 받지 않고 토큰에서만.
 * @param role      가족 내 역할 (PARENT/CHILD)
 */
public record AuthUser(
        Long personId,
        Long accountId,
        Long familyId,
        FamilyRole role
) {
    public boolean isParent() {
        return role == FamilyRole.PARENT;
    }
}
