package com.familyos.collection.service;

import com.familyos.collection.dto.CollectionRequest;
import com.familyos.collection.dto.CollectionResponse;
import com.familyos.collection.entity.Collection;
import com.familyos.collection.mapper.CollectionSummaryMapper;
import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.error.BusinessException;
import com.familyos.tag.entity.Tag;
import com.familyos.tag.repository.CollectionTagRepository;
import com.familyos.tag.repository.TagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CollectionService 태그 연동 검증: alive 검증(422) + 생성 시 CollectionTag 저장, 응답에 태그명 반영.
 *
 * <p>주의: 이 환경에서는 빌드/실행 미검증. 의도와 규칙을 코드로 고정하는 용도.
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    private static final Long FAMILY_ID = 1L;

    @Mock CollectionRepository collectionRepository;
    @Mock CollectionSummaryMapper summaryMapper;
    @Mock CollectionTagRepository collectionTagRepository;
    @Mock TagRepository tagRepository;
    @Mock SoftDeleteSupport softDeleteSupport;

    CollectionService service;

    @BeforeEach
    void setUp() {
        service = new CollectionService(collectionRepository, summaryMapper, collectionTagRepository, tagRepository, softDeleteSupport);
        FamilyContext.set(new AuthUser(10L, 1L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    @Test
    void create_rejectsDeletedOrMissingTag() {
        CollectionRequest req = new CollectionRequest("제주 여행", null, null, null, List.of(99L));
        when(tagRepository.findByIdInAndFamilyId(Set.of(99L), FAMILY_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_savesTagLinks_andReturnsTagNames() {
        Tag tag = new Tag(FAMILY_ID, "여행");
        setId(tag, 5L);
        when(tagRepository.findByIdInAndFamilyId(Set.of(5L), FAMILY_ID)).thenReturn(List.of(tag));

        Collection saved = new Collection(FAMILY_ID, "제주 여행", null, null, null);
        setId(saved, 100L);
        when(collectionRepository.save(any(Collection.class))).thenReturn(saved);

        CollectionRequest req = new CollectionRequest("제주 여행", null, null, null, List.of(5L));
        CollectionResponse response = service.create(req);

        assertThat(response.tags()).containsExactly("여행");
        verify(collectionTagRepository).save(any());
    }

    /** BaseEntity.id 는 리플렉션 없이 못 세팅해서 테스트 전용 헬퍼로 우회. */
    private void setId(Object entity, Long id) {
        try {
            var field = com.familyos.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
