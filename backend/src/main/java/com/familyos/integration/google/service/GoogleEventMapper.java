package com.familyos.integration.google.service;

import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.MappedSchedule;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 구글 이벤트 → 우리 Schedule 필드 매핑 (docs/08 §1-6 + 시각/날짜 이원화 1-6).
 *
 * <ul>
 *   <li>summary→title(없으면 "(제목 없음)" — DDL title NOT NULL), description, location</li>
 *   <li>종일(start.date): startDate/endDate (구글 end.date 는 배타적 → -1일), allDay=true</li>
 *   <li>시점(start.dateTime): startedAt/endedAt(UTC Instant), allDay=false</li>
 *   <li>recurrence(RRULE 목록) → recurrenceRule(첫 RRULE)</li>
 * </ul>
 */
@Component
public class GoogleEventMapper {

    private static final String NO_TITLE = "(제목 없음)";

    /** 매핑 불가(시작 정보 없음)면 null → 호출측에서 skip. */
    public @Nullable MappedSchedule map(GoogleEvent ev) {
        if (ev.start() == null) {
            return null;
        }
        String title = (ev.summary() == null || ev.summary().isBlank()) ? NO_TITLE : ev.summary();
        String recurrence = firstRrule(ev.recurrence());

        boolean allDay = ev.start().date() != null;
        if (allDay) {
            LocalDate startDate = LocalDate.parse(ev.start().date());
            LocalDate endDate = startDate;
            if (ev.end() != null && ev.end().date() != null) {
                // 구글 종일 이벤트의 end.date 는 마지막 날 +1 (배타적) → -1일 보정
                LocalDate exclusiveEnd = LocalDate.parse(ev.end().date());
                LocalDate inclusive = exclusiveEnd.minusDays(1);
                endDate = inclusive.isBefore(startDate) ? startDate : inclusive;
            }
            return new MappedSchedule(title, ev.description(), ev.location(),
                    null, null, startDate, endDate, true, recurrence);
        }

        Instant startedAt = parseInstant(ev.start().dateTime());
        if (startedAt == null) {
            return null; // dateTime/date 둘 다 없음 → 배치 불가
        }
        Instant endedAt = ev.end() == null ? null : parseInstant(ev.end().dateTime());
        return new MappedSchedule(title, ev.description(), ev.location(),
                startedAt, endedAt, null, null, false, recurrence);
    }

    private @Nullable Instant parseInstant(@Nullable String rfc3339) {
        if (rfc3339 == null || rfc3339.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(rfc3339).toInstant();
    }

    private @Nullable String firstRrule(@Nullable List<String> recurrence) {
        if (recurrence == null) {
            return null;
        }
        return recurrence.stream()
                .filter(r -> r != null && r.startsWith("RRULE"))
                .findFirst()
                .orElse(null);
    }
}
