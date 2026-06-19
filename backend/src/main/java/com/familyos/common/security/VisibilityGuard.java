package com.familyos.common.security;

import com.familyos.common.context.AuthUser;
import com.familyos.common.entity.Visible;
import com.familyos.common.error.NotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 단건 조회/수정 시 visibility 권한 판정 — 2단계 방어의 ②.
 *
 * <p><b>위반은 404(숨김)</b> 로 응답한다(403 아님 — 존재 자체를 노출하지 않음).
 * 목록 조회의 visibility 필터링은 여기가 아니라 DB 쿼리 WHERE 에서 한다(메모리 필터 금지).
 *
 * <p>전제: 자원은 이미 family 필터로 로딩되었다고 본다. 타 가족 자원은 애초에 로딩되지 않아
 * 서비스가 NotFound 를 던지므로, 이 가드는 같은 family 내 visibility 만 판정한다.
 *
 * <p>판정 규칙:
 * <ul>
 *   <li>PRIVATE — author(created_by) 본인만</li>
 *   <li>SHARED_PERSONAL — author + subject 지정자</li>
 *   <li>PARENTS — role=PARENT 전원</li>
 *   <li>FAMILY — family 전원(이미 격리됨)</li>
 * </ul>
 */
@Component
public class VisibilityGuard {

    /** 단건 조회 권한. 위반 시 404. */
    public void assertCanView(@Nullable Visible resource, AuthUser user) {
        if (resource == null || !canView(resource, user)) {
            throw hidden();
        }
    }

    /**
     * 수정/삭제 권한. 기본 규칙: <b>author 본인만</b>.
     * 위반 시 404 (조회조차 불가한 경우와 구분 없이 숨김). 가족 공동 자원에 대한 PARENT 관리권은
     * {@link #assertCanEditAllowingParentOnShared} 로 도메인이 선택 적용.
     */
    public void assertCanEdit(@Nullable Visible resource, AuthUser user) {
        if (resource == null || !canView(resource, user)) {
            throw hidden();
        }
        if (!isAuthor(resource, user)) {
            throw hidden();
        }
    }

    /**
     * author 본인 또는 (자원이 PARENTS/FAMILY 공동범위일 때) PARENT 에게 수정 허용.
     * 도메인 정책상 부모가 가족 공동 자원을 관리할 수 있는 경우에만 사용.
     */
    public void assertCanEditAllowingParentOnShared(@Nullable Visible resource, AuthUser user) {
        if (resource == null || !canView(resource, user)) {
            throw hidden();
        }
        if (isAuthor(resource, user)) {
            return;
        }
        boolean shared = switch (resource.getVisibility()) {
            case PARENTS, FAMILY -> true;
            case PRIVATE, SHARED_PERSONAL -> false;
        };
        if (!(shared && user.isParent())) {
            throw hidden();
        }
    }

    private boolean canView(Visible resource, AuthUser user) {
        return switch (resource.getVisibility()) {
            case PRIVATE -> isAuthor(resource, user);
            case SHARED_PERSONAL -> isAuthor(resource, user)
                    || resource.getSubjectPersonIds().contains(user.personId());
            case PARENTS -> user.isParent();
            case FAMILY -> true;
        };
    }

    private boolean isAuthor(Visible resource, AuthUser user) {
        Long author = resource.getCreatedBy();
        return author != null && author.equals(user.personId());
    }

    private NotFoundException hidden() {
        return new NotFoundException("요청한 자원을 찾을 수 없습니다.");
    }
}
