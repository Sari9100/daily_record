package com.familyos.integration.telegram.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.integration.telegram.dto.TelegramLinkRequest;
import com.familyos.integration.telegram.service.TelegramService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 텔레그램 계정 연결/해제 (JWT 인증). 연결 후 Hermes 가 그 telegram_user_id 로 본인을 대행할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/me/telegram")
public class MeTelegramController {

    private final TelegramService telegramService;

    public MeTelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping
    public ApiResponse<Void> link(@Valid @RequestBody TelegramLinkRequest request) {
        telegramService.link(request.telegramUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> unlink() {
        telegramService.unlink();
        return ApiResponse.ok();
    }
}
