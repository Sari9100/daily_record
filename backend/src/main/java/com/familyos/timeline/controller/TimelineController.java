package com.familyos.timeline.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.timeline.dto.TimelineResponse;
import com.familyos.timeline.service.TimelineService;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 통합 타임라인 API.
 * - {@code GET /api/v1/timeline?date=2026-06-19} (단일 날짜)
 * - {@code GET /api/v1/timeline?from=2026-06-01&to=2026-06-30} (기간)
 */
@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public ApiResponse<TimelineResponse> timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        return ApiResponse.ok(timelineService.timeline(date, from, to));
    }
}
