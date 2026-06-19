package com.familyos.timeline.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.person.entity.Person;
import com.familyos.person.repository.PersonRepository;
import com.familyos.storage.UrlSigner;
import com.familyos.timeline.dto.TimelineResponse;
import com.familyos.timeline.dto.TimelineResponse.TimelineDay;
import com.familyos.timeline.dto.TimelineResponse.TimelineItem;
import com.familyos.timeline.dto.TimelineRow;
import com.familyos.timeline.mapper.TimelineMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 타임라인(MyBatis). 4개 도메인을 날짜축으로 병합, visibility+family 는 매퍼 WHERE 에서 처리.
 * 시각 포맷·그룹핑·사진 썸네일(서명 URL)은 서비스에서 조립.
 */
@Service
@Transactional(readOnly = true)
public class TimelineService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final TimelineMapper timelineMapper;
    private final PersonRepository personRepository;
    private final UrlSigner urlSigner;

    public TimelineService(TimelineMapper timelineMapper, PersonRepository personRepository, UrlSigner urlSigner) {
        this.timelineMapper = timelineMapper;
        this.personRepository = personRepository;
        this.urlSigner = urlSigner;
    }

    /** date(단일) 또는 from~to(기간). date 가 있으면 우선. */
    public TimelineResponse timeline(@Nullable LocalDate date, @Nullable LocalDate from, @Nullable LocalDate to) {
        AuthUser user = FamilyContext.require();
        LocalDate fromDate = date != null ? date : from;
        LocalDate toDate = date != null ? date : to;
        if (fromDate == null || toDate == null) {
            throw new BusinessException("date 또는 from/to 를 지정해야 합니다.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("from 은 to 보다 클 수 없습니다.");
        }

        ZoneId zone = viewerZone(user.personId());
        String zoneOffset = offsetOf(zone);

        List<TimelineRow> rows = timelineMapper.timeline(
                user.familyId(), user.personId(), user.isParent(), fromDate, toDate, zoneOffset);

        // 매퍼가 (group_date, 시각없음 우선, 시각, type) 순으로 정렬해 반환 → 순서 유지하며 날짜로 그룹핑
        Map<LocalDate, List<TimelineItem>> byDate = new LinkedHashMap<>();
        for (TimelineRow row : rows) {
            byDate.computeIfAbsent(row.groupDate(), d -> new ArrayList<>()).add(toItem(row, zone));
        }

        List<TimelineDay> days = byDate.entrySet().stream()
                .map(e -> new TimelineDay(e.getKey(), e.getValue()))
                .toList();
        return new TimelineResponse(days);
    }

    private TimelineItem toItem(TimelineRow row, ZoneId zone) {
        String time = row.sortInstant() == null ? null : row.sortInstant().atZone(zone).format(HHMM);
        String thumbnailUrl = "PHOTO".equals(row.type()) ? signedPhotoUrl(row.id()) : null;
        return new TimelineItem(row.type(), row.id(), time, row.title(), row.amount(), thumbnailUrl);
    }

    private String signedPhotoUrl(Long photoId) {
        long exp = urlSigner.expiryEpochSecond();
        return "/api/v1/photos/" + photoId + "/raw?exp=" + exp + "&sig=" + urlSigner.sign(photoId, exp);
    }

    private ZoneId viewerZone(Long personId) {
        return personRepository.findById(personId)
                .map(Person::getTimezone)
                .map(ZoneId::of)
                .orElseThrow(() -> NotFoundException.of("사용자", personId));
    }

    /** MySQL CONVERT_TZ 용 offset("+09:00"). UTC 는 "Z" 대신 "+00:00". */
    private String offsetOf(ZoneId zone) {
        ZoneOffset offset = zone.getRules().getOffset(Instant.now());
        String id = offset.getId();
        return "Z".equals(id) ? "+00:00" : id;
    }
}
