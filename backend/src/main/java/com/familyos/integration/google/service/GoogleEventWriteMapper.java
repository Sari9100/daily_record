package com.familyos.integration.google.service;

import com.familyos.integration.google.dto.GoogleEventWrite;
import com.familyos.integration.google.dto.GoogleEventWrite.EventDateWrite;
import com.familyos.schedule.entity.Schedule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 우리 Schedule → 구글 이벤트 본문 (Phase 1 매핑의 역방향).
 *
 * <ul>
 *   <li>title→summary, description, location</li>
 *   <li>종일: start.date=startDate, end.date=endDate+1(구글 배타적 end 복원)</li>
 *   <li>시점: start.dateTime=startedAt(UTC), end.dateTime=endedAt(없으면 startedAt)</li>
 *   <li>recurrenceRule→recurrence</li>
 * </ul>
 */
@Component
public class GoogleEventWriteMapper {

    public GoogleEventWrite toWrite(Schedule s) {
        EventDateWrite start;
        EventDateWrite end;
        if (s.isAllDay()) {
            LocalDate startDate = requireNonNull(s.getStartDate(), "종일 일정에 startDate 가 없습니다.");
            LocalDate endInclusive = s.getEndDate() != null ? s.getEndDate() : startDate;
            start = new EventDateWrite(null, startDate.toString());
            end = new EventDateWrite(null, endInclusive.plusDays(1).toString()); // 구글 end 는 배타적 → +1
        } else {
            Instant startedAt = requireNonNull(s.getStartedAt(), "시점 일정에 startedAt 이 없습니다.");
            Instant endedAt = s.getEndedAt() != null ? s.getEndedAt() : startedAt;
            start = new EventDateWrite(startedAt.toString(), null);
            end = new EventDateWrite(endedAt.toString(), null);
        }
        List<String> recurrence = s.getRecurrenceRule() == null ? null : List.of(s.getRecurrenceRule());
        return new GoogleEventWrite(s.getTitle(), s.getDescription(), s.getLocation(), start, end, recurrence);
    }

    private <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
