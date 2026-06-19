package com.familyos.tag;

import com.familyos.support.AbstractIntegrationTest;
import com.familyos.tag.entity.Tag;
import com.familyos.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실 MySQL 통합테스트: family 격리 + alive_uk 유니크(soft-delete 환경 중복 차단).
 * 단위테스트(Mockito)가 검증 못 하는 DB 제약을 실제로 확인. @Transactional 로 테스트별 롤백.
 */
@Transactional
class TagRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TagRepository tagRepository;

    @Test
    void 같은_가족_같은이름_alive_중복은_DB가_차단한다() {
        tagRepository.saveAndFlush(new Tag(1L, "여행"));
        // alive_uk(=0) 동일 → uk_tag(family_id, name, alive_uk) 위반
        assertThatThrownBy(() -> tagRepository.saveAndFlush(new Tag(1L, "여행")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_가족의_태그는_조회되지_않는다() {
        tagRepository.saveAndFlush(new Tag(1L, "캠핑"));
        tagRepository.saveAndFlush(new Tag(2L, "캠핑"));

        assertThat(tagRepository.findByFamilyIdOrderByNameAsc(1L))
                .extracting(Tag::getName).containsExactly("캠핑");
        assertThat(tagRepository.findByFamilyIdOrderByNameAsc(1L))
                .allSatisfy(t -> assertThat(t.getFamilyId()).isEqualTo(1L));
    }
}
