package com.familyos.ledger.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.common.web.PageResponse;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.dto.TransactionResponse;
import com.familyos.ledger.entity.TransactionType;
import com.familyos.ledger.service.TransactionService;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant to,
            @RequestParam(required = false) @Nullable TransactionType type,
            @RequestParam(required = false) @Nullable Long categoryId,
            @RequestParam(required = false) @Nullable String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(transactionService.list(from, to, type, categoryId, scope, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ApiResponse.ok(transactionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody TransactionRequest request) {
        return ApiResponse.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ApiResponse.ok();
    }
}
