package com.familyos.common.entity;

import com.familyos.common.domain.Visibility;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * {@link Visible} 의 경량 구현체.
 *
 * <p>subject 가 별도 테이블에 있는 도메인(일정·일상기록)에서, 엔티티가 직접 Visible 을 구현하기 어려울 때
 * 서비스가 (visibility, author, subjectPersonIds)를 모아 만들어 VisibilityGuard 에 넘긴다.
 */
public record VisibleResource(
        Visibility visibility,
        @Nullable Long createdBy,
        Set<Long> subjectPersonIds
) implements Visible {

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public @Nullable Long getCreatedBy() {
        return createdBy;
    }

    @Override
    public Set<Long> getSubjectPersonIds() {
        return subjectPersonIds;
    }
}
