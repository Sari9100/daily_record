package com.familyos.person.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.person.dto.PersonSettingResponse;
import com.familyos.person.dto.UpdatePersonSettingRequest;
import com.familyos.person.service.PersonSettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 개인 설정 API. 본인(FamilyContext.personId) 기준. */
@RestController
@RequestMapping("/api/v1/me/settings")
public class MeSettingsController {

    private final PersonSettingService settingService;

    public MeSettingsController(PersonSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public ApiResponse<PersonSettingResponse> getSettings() {
        return ApiResponse.ok(settingService.getMySettings());
    }

    @PutMapping
    public ApiResponse<PersonSettingResponse> updateSettings(
            @Valid @RequestBody UpdatePersonSettingRequest request) {
        return ApiResponse.ok(settingService.updateMySettings(
                request.sharedScheduleDetailLevel(),
                request.ledgerDefaultView(),
                request.scheduleDefaultView(),
                request.diaryDefaultView()));
    }
}
