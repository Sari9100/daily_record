package com.familyos.diary.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.diary.dto.DiaryRequest;
import com.familyos.diary.dto.DiaryResponse;
import com.familyos.diary.service.DiaryService;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 일상기록 API. 날짜축은 recordedOn 이므로 from/to 는 LocalDate.
 * view(feed|calendar|album)는 응답 형태 동일 — 클라가 렌더 구분(서버 무시).
 */
@RestController
@RequestMapping("/api/v1/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @GetMapping
    public ApiResponse<List<DiaryResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable String view,
            @RequestParam(required = false) @Nullable String scope) {
        return ApiResponse.ok(diaryService.list(from, to, scope));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiaryResponse> create(@Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(diaryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiaryResponse> update(@PathVariable Long id, @Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(diaryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        diaryService.delete(id);
        return ApiResponse.ok();
    }
}
