package com.familyos.schedule.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.schedule.dto.ScheduleRequest;
import com.familyos.schedule.dto.ScheduleResponse;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> list(
            @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant to,
            @RequestParam(required = false) @Nullable ScheduleType type,
            @RequestParam(required = false) @Nullable String scope,
            @RequestParam(required = false) @Nullable String q) {
        return ApiResponse.ok(scheduleService.list(from, to, type, scope, q));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(scheduleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(scheduleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.ok();
    }

    /** TODO 완료 토글. */
    @PatchMapping("/{id}/done")
    public ApiResponse<ScheduleResponse> toggleDone(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.toggleDone(id));
    }
}
