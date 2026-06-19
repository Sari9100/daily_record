package com.familyos.ledger.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.ledger.dto.AccountRequest;
import com.familyos.ledger.dto.AccountResponse;
import com.familyos.ledger.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<AccountResponse>> list() {
        return ApiResponse.ok(accountService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok(accountService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok(accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ApiResponse.ok();
    }
}
