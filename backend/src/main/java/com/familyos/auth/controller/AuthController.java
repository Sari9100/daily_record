package com.familyos.auth.controller;

import com.familyos.auth.dto.ChangePasswordRequest;
import com.familyos.auth.dto.LoginRequest;
import com.familyos.auth.dto.LoginResponse;
import com.familyos.auth.dto.LogoutRequest;
import com.familyos.auth.dto.MeResponse;
import com.familyos.auth.dto.RefreshRequest;
import com.familyos.auth.dto.TokenResponse;
import com.familyos.auth.service.AuthService;
import com.familyos.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. /login·/refresh 는 permitAll(SecurityConfig), /logout·/me 는 인증 필요.
 * family_id 는 토큰에서만 결정된다 — 요청으로 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    /** 본인 비밀번호 변경 — 성공 시 이 계정의 모든 세션(현재 기기 포함)이 로그아웃된다. */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
        return ApiResponse.ok();
    }
}
