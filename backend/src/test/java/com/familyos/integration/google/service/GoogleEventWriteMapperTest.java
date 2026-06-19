package com.familyos.integration.google.service;

import com.familyos.common.domain.Visibility;
import com.familyos.integration.google.dto.GoogleEventWrite;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schedule → 구글 이벤트 역방향 매핑(Phase 2): 시점/종일, 종일 end +1(배타적 복원), RRULE.
 */
class GoogleEventWriteMapperTest {

    private final GoogleEventWriteMapper mapper = new GoogleEventWriteMapper();

    private Schedule timed(Instant start, Instant end, String recurrence) {
        return new Schedule(1L, "회의", "주간", "회의실", start, end, null, null, false,
                Visibility.PRIVATE, ScheduleType.EVENT, recurrence, null);
    }

    private Schedule allDay(LocalDate startDate, LocalDate endDate) {
        return new Schedule(1L, "여행", null, null, null, null, startDate, endDate, true,
                Visibility.PRIVATE, ScheduleType.EVENT, null, null);
    }

    @Test
    void 시점일정은_dateTime으로_전송된다() {
        GoogleEventWrite w = mapper.toWrite(
                timed(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T01:00:00Z"), null));

        assertThat(w.summary()).isEqualTo("회의");
        assertThat(w.start().dateTime()).isEqualTo("2026-07-01T00:00:00Z");
        assertThat(w.start().date()).isNull();
        assertThat(w.end().dateTime()).isEqualTo("2026-07-01T01:00:00Z");
    }

    @Test
    void 종일일정은_date로_전송되고_end는_배타적_plus1() {
        // 우리 endDate(07-02, 포함) → 구글 end.date 07-03(배타적)
        GoogleEventWrite w = mapper.toWrite(
                allDay(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02")));

        assertThat(w.start().date()).isEqualTo("2026-07-01");
        assertThat(w.start().dateTime()).isNull();
        assertThat(w.end().date()).isEqualTo("2026-07-03");
    }

    @Test
    void RRULE은_recurrence로_전송된다() {
        GoogleEventWrite w = mapper.toWrite(timed(
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T01:00:00Z"),
                "RRULE:FREQ=WEEKLY;BYDAY=MO"));

        assertThat(w.recurrence()).containsExactly("RRULE:FREQ=WEEKLY;BYDAY=MO");
    }
}
