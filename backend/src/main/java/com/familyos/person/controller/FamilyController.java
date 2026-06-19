package com.familyos.person.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.person.dto.AccountCreatedResponse;
import com.familyos.person.dto.AddMemberRequest;
import com.familyos.person.dto.CreateAccountRequest;
import com.familyos.person.dto.FamilyOverviewResponse;
import com.familyos.person.dto.MemberCreatedResponse;
import com.familyos.person.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가족·구성원·계정 관리 API. family_id 는 토큰(FamilyContext)에서만 — 경로/본문으로 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/family")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping
    public ApiResponse<FamilyOverviewResponse> getFamily() {
        return ApiResponse.ok(familyService.getCurrentFamily());
    }

    @PostMapping("/members")
    @PreAuthorize("hasRole('PARENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberCreatedResponse> addMember(@Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.ok(familyService.addMember(request));
    }

    @PostMapping("/members/{personId}/account")
    @PreAuthorize("hasRole('PARENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountCreatedResponse> createAccount(@PathVariable Long personId,
                                                             @Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.ok(familyService.createAccountForMember(personId, request));
    }
}
