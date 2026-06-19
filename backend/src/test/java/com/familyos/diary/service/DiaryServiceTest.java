package com.familyos.diary.service;

import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.diary.dto.DiaryRequest;
import com.familyos.diary.repository.DiaryRepository;
import com.familyos.diary.repository.DiarySubjectRepository;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.tag.repository.DiaryTagRepository;
import com.familyos.tag.repository.TagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DiaryService 핵심 규칙(필수): 참조 alive 검증(subject/collection/tag). 빌드/실행은 이 환경에서 미검증.
 */
@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final Long FAMILY_ID = 1L;

    @Mock DiaryRepository diaryRepository;
    @Mock DiarySubjectRepository subjectRepository;
    @Mock DiaryTagRepository diaryTagRepository;
    @Mock TagRepository tagRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock CollectionRepository collectionRepository;
    @Mock VisibilityGuard visibilityGuard;
    @Mock SoftDeleteSupport softDeleteSupport;

    DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(diaryRepository, subjectRepository, diaryTagRepository, tagRepository,
                membershipRepository, collectionRepository, visibilityGuard, softDeleteSupport);
        FamilyContext.set(new AuthUser(1L, 10L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private DiaryRequest req(List<Long> subjects, Long collectionId, List<Long> tagIds) {
        return new DiaryRequest("주말", "내용", Visibility.FAMILY,
                LocalDate.parse("2026-06-15"), null, collectionId, subjects, tagIds);
    }

    @Test
    void subject가_가족구성원이_아니면_422() {
        when(membershipRepository.existsByFamily_IdAndPerson_Id(FAMILY_ID, 99L)).thenReturn(false);
        assertThatThrownBy(() -> service.create(req(List.of(99L), null, List.of())))
                .isInstanceOf(BusinessException.class);
        verify(diaryRepository, never()).save(any());
    }

    @Test
    void 삭제된_묶음_참조시_422() {
        when(collectionRepository.findByIdAndFamilyId(5L, FAMILY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(req(List.of(), 5L, List.of())))
                .isInstanceOf(BusinessException.class);
        verify(diaryRepository, never()).save(any());
    }

    @Test
    void 삭제된_태그_연결시_422() {
        when(tagRepository.findByIdInAndFamilyId(any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(req(List.of(), null, List.of(7L))))
                .isInstanceOf(BusinessException.class);
        verify(diaryRepository, never()).save(any());
    }
}
