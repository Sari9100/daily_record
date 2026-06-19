package com.familyos.timeline.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.person.entity.Person;
import com.familyos.person.repository.PersonRepository;
import com.familyos.storage.UrlSigner;
import com.familyos.timeline.dto.TimelineResponse;
import com.familyos.timeline.dto.TimelineRow;
import com.familyos.timeline.mapper.TimelineMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TimelineService 조립 로직(필수 외 보강): 날짜 그룹핑, 로컬 시각 포맷, 시각없는 항목 처리, 사진 썸네일.
 * SQL(UNION/visibility/family) 자체는 실DB 통합테스트 영역. 빌드/실행은 이 환경에서 미검증.
 */
@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock TimelineMapper timelineMapper;
    @Mock PersonRepository personRepository;
    @Mock UrlSigner urlSigner;

    TimelineService service;

    @BeforeEach
    void setUp() {
        service = new TimelineService(timelineMapper, personRepository, urlSigner);
        FamilyContext.set(new AuthUser(1L, 10L, 1L, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    @Test
    void 날짜로_그룹핑하고_로컬시각_포맷_및_사진썸네일을_만든다() {
        LocalDate day = LocalDate.parse("2026-06-19");
        when(personRepository.findById(1L)).thenReturn(Optional.of(new Person("나", null, "Asia/Seoul")));
        lenient().when(urlSigner.expiryEpochSecond()).thenReturn(1_000L);
        lenient().when(urlSigner.sign(anyLong(), anyLong())).thenReturn("sig");

        // 매퍼가 정렬해 반환한다고 가정: 종일 일정(시각없음) → 시각순(거래 11:30, 사진 13:00)
        List<TimelineRow> rows = List.of(
                new TimelineRow("SCHEDULE", 3L, day, null, "종일행사", null),
                new TimelineRow("TRANSACTION", 10L, day, Instant.parse("2026-06-19T02:30:00Z"), "마트", new BigDecimal("35000")),
                new TimelineRow("PHOTO", 55L, day, Instant.parse("2026-06-19T04:00:00Z"), null, null));
        when(timelineMapper.timeline(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(rows);

        TimelineResponse res = service.timeline(day, null, null);

        assertThat(res.days()).hasSize(1);
        TimelineResponse.TimelineDay d = res.days().getFirst();
        assertThat(d.date()).isEqualTo(day);
        assertThat(d.items()).hasSize(3);

        // 종일 일정: 시각 null, 맨 위
        assertThat(d.items().get(0).type()).isEqualTo("SCHEDULE");
        assertThat(d.items().get(0).time()).isNull();
        // 거래: 02:30Z → KST 11:30
        assertThat(d.items().get(1).type()).isEqualTo("TRANSACTION");
        assertThat(d.items().get(1).time()).isEqualTo("11:30");
        assertThat(d.items().get(1).amount()).isEqualByComparingTo("35000");
        // 사진: 04:00Z → 13:00, 서명 썸네일 URL
        assertThat(d.items().get(2).type()).isEqualTo("PHOTO");
        assertThat(d.items().get(2).time()).isEqualTo("13:00");
        assertThat(d.items().get(2).thumbnailUrl()).contains("/api/v1/photos/55/raw?exp=");
    }
}
