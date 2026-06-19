package com.familyos.tag;

import com.familyos.person.entity.Family;
import com.familyos.person.repository.FamilyRepository;
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
 *
 * <p>Tag.family_id 는 family(id) FK 이므로, 먼저 Family 를 만들고 그 id 로 Tag 를 생성한다.
 */
@Transactional
class TagRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TagRepository tagRepository;
    @Autowired
    FamilyRepository familyRepository;

    @Test
    void 같은_가족_같은이름_alive_중복은_DB가_차단한다() {
        Long familyId = familyRepository.saveAndFlush(new Family("가족A")).getId();
        tagRepository.saveAndFlush(new Tag(familyId, "여행"));

        // alive_uk(=0) 동일 → uk_tag(family_id, name, alive_uk) 위반
        assertThatThrownBy(() -> tagRepository.saveAndFlush(new Tag(familyId, "여행")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_가족의_태그는_조회되지_않는다() {
        Long familyId1 = familyRepository.saveAndFlush(new Family("가족1")).getId();
        Long familyId2 = familyRepository.saveAndFlush(new Family("가족2")).getId();
        tagRepository.saveAndFlush(new Tag(familyId1, "캠핑"));
        tagRepository.saveAndFlush(new Tag(familyId2, "캠핑"));

        assertThat(tagRepository.findByFamilyIdOrderByNameAsc(familyId1))
                .extracting(Tag::getName).containsExactly("캠핑");
        assertThat(tagRepository.findByFamilyIdOrderByNameAsc(familyId1))
                .allSatisfy(t -> assertThat(t.getFamilyId()).isEqualTo(familyId1));
    }
}
