package com.familyos.integration.telegram.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.integration.telegram.dto.TelegramScheduleDeleteRequest;
import com.familyos.integration.telegram.dto.TelegramScheduleRequest;
import com.familyos.integration.telegram.service.TelegramScheduleService;
import com.familyos.schedule.dto.ScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hermes 내부 API — 텔레그램 일정 <b>쓰기</b>(생성/수정/삭제). 서비스 토큰 인증({@code HermesServiceTokenFilter}).
 *
 * <p>조회(GET {@code /schedules})는 {@code TelegramController} 에 있다. 여기서는 쓰기만 담당하며
 * 같은 base path 를 공유하되 HTTP 메서드가 달라 매핑이 충돌하지 않는다. 행동 주체는 본문 telegramUserId 로 결정.
 */
@RestController
@RequestMapping("/api/v1/integrations/telegram")
public class TelegramScheduleController {

    private final TelegramScheduleService telegramScheduleService;

    public TelegramScheduleController(TelegramScheduleService telegramScheduleService) {
        this.telegramScheduleService = telegramScheduleService;
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody TelegramScheduleRequest request) {
        return ApiResponse.ok(telegramScheduleService.create(request.telegramUserId(), request.schedule()));
    }

    @PutMapping("/schedules/{id}")
    public ApiResponse<ScheduleResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody TelegramScheduleRequest request) {
        return ApiResponse.ok(telegramScheduleService.update(request.telegramUserId(), id, request.schedule()));
    }

    @DeleteMapping("/schedules/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @Valid @RequestBody TelegramScheduleDeleteRequest request) {
        telegramScheduleService.delete(request.telegramUserId(), id);
        return ApiResponse.ok();
    }
}
