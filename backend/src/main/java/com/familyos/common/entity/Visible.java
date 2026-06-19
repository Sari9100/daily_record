package com.familyos.common.entity;

import com.familyos.common.domain.Visibility;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * visibility 권한 판정({@code VisibilityGuard})의 대상 계약.
 *
 * <p>common 이 도메인 엔티티 타입에 의존하지 않도록, 권한 판정에 필요한 최소 정보만 노출한다.
 * Transaction/Schedule/Diary/Account 등 visibility 를 가지는 엔티티가 구현한다.
 */
public interface Visible {

    /** 자원의 공개 범위. */
    Visibility getVisibility();

    /** 작성자(author) = created_by. PRIVATE/SHARED_PERSONAL 판정 기준. */
    @Nullable Long getCreatedBy();

    /**
     * SHARED_PERSONAL 자원의 subject(대상자) Person.id 집합.
     * 일정/일상기록만 의미 있고, 가계부 등 subject 개념이 없으면 빈 집합(기본).
     */
    default Set<Long> getSubjectPersonIds() {
        return Set.of();
    }
}
