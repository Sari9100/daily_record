package com.familyos.ledger.controller;

import com.familyos.common.web.ApiResponse;
import com.familyos.common.web.PageResponse;
import com.familyos.ledger.dto.LinkTransactionsToCollectionRequest;
import com.familyos.ledger.dto.StatisticsResponse;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.dto.TransactionResponse;
import com.familyos.ledger.entity.TransactionType;
import com.familyos.ledger.service.TransactionService;
import com.familyos.ledger.service.TransactionStatisticsService;
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
    private final TransactionStatisticsService statisticsService;

    public TransactionController(TransactionService transactionService,
                                 TransactionStatisticsService statisticsService) {
        this.transactionService = transactionService;
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant to,
            @RequestParam(required = false) @Nullable TransactionType type,
            @RequestParam(required = false) @Nullable Long categoryId,
            @RequestParam(required = false) @Nullable Long collectionId,
            @RequestParam(required = false) @Nullable String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(transactionService.list(from, to, type, categoryId, collectionId, scope, page, size));
    }

    /**
     * 통계(MyBatis). TRANSFER 제외, 카테고리별/월별 집계. groupBy 는 호환용 파라미터(응답은 항상 전체 구조).
     * /{id} 보다 먼저 매칭되도록 리터럴 경로.
     */
    @GetMapping("/statistics")
    public ApiResponse<StatisticsResponse> statistics(
            @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant to,
            @RequestParam(required = false) @Nullable String groupBy,
            @RequestParam(required = false) @Nullable String scope) {
        return ApiResponse.ok(statisticsService.statistics(from, to, scope));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ApiResponse.ok(transactionService.create(request));
    }

    /** 가계부 다중선택 → 묶음 일괄연결. */
    @PostMapping("/link-collection")
    public ApiResponse<Integer> linkCollection(@Valid @RequestBody LinkTransactionsToCollectionRequest request) {
        return ApiResponse.ok(transactionService.linkToCollection(request.transactionIds(), request.collectionId()));
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
