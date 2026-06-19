package com.familyos.integration.google.service;

import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.GoogleEvent.GoogleEventDateTime;
import com.familyos.integration.google.dto.MappedSchedule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구글 이벤트 → Schedule 매핑(검증 가능한 핵심): 시점/종일 이원화, 종일 end 배타적 보정, 기본 제목.
 */
class GoogleEventMapperTest {

    private final GoogleEventMapper mapper = new GoogleEventMapper();

    private GoogleEventDateTime timed(String dateTime) {
        return new GoogleEventDateTime(dateTime, null, null);
    }

    private GoogleEventDateTime allDay(String date) {
        return new GoogleEventDateTime(null, date, null);
    }

    @Test
    void 시점_이벤트는_startedAt_endedAt_으로_매핑된다() {
        GoogleEvent ev = new GoogleEvent("g1", "confirmed", "회의", "주간", "회의실",
                timed("2026-07-01T09:00:00+09:00"), timed("2026-07-01T10:00:00+09:00"), null);

        MappedSchedule m = mapper.map(ev);

        assertThat(m).isNotNull();
        assertThat(m.allDay()).isFalse();
        assertThat(m.startedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z")); // 09:00 KST = 00:00 UTC
        assertThat(m.endedAt()).isEqualTo(Instant.parse("2026-07-01T01:00:00Z"));
        assertThat(m.startDate()).isNull();
        assertThat(m.title()).isEqualTo("회의");
    }

    @Test
    void 종일_이벤트는_endDate를_배타적_보정해서_매핑된다() {
        // 구글 종일: 7/1 하루 → start.date=2026-07-01, end.date=2026-07-02(배타적)
        GoogleEvent ev = new GoogleEvent("g2", "confirmed", "기념일", null, null,
                allDay("2026-07-01"), allDay("2026-07-02"), null);

        MappedSchedule m = mapper.map(ev);

        assertThat(m).isNotNull();
        assertThat(m.allDay()).isTrue();
        assertThat(m.startDate()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(m.endDate()).isEqualTo(LocalDate.parse("2026-07-01")); // 02 → -1일 = 01
        assertThat(m.startedAt()).isNull();
    }

    @Test
    void 제목이_없으면_기본_제목으로_채운다() {
        GoogleEvent ev = new GoogleEvent("g3", "confirmed", null, null, null,
                timed("2026-07-01T09:00:00Z"), null, null);

        assertThat(mapper.map(ev).title()).isEqualTo("(제목 없음)");
    }

    @Test
    void RRULE은_recurrenceRule로_옮겨진다() {
        GoogleEvent ev = new GoogleEvent("g4", "confirmed", "반복", null, null,
                timed("2026-07-01T09:00:00Z"), timed("2026-07-01T10:00:00Z"),
                List.of("RRULE:FREQ=WEEKLY;BYDAY=MO"));

        assertThat(mapper.map(ev).recurrenceRule()).isEqualTo("RRULE:FREQ=WEEKLY;BYDAY=MO");
    }

    @Test
    void 시작정보가_없으면_매핑하지_않는다() {
        GoogleEvent ev = new GoogleEvent("g5", "confirmed", "x", null, null, null, null, null);
        assertThat(mapper.map(ev)).isNull();
    }
}
