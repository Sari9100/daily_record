package com.familyos.collection.controller;

import com.familyos.collection.dto.CollectionRequest;
import com.familyos.collection.dto.CollectionResponse;
import com.familyos.collection.dto.CollectionSummaryResponse;
import com.familyos.collection.service.CollectionService;
import com.familyos.common.web.ApiResponse;
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
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public ApiResponse<List<CollectionResponse>> list() {
        return ApiResponse.ok(collectionService.list());
    }

    /** 묶음 요약 — 연결된 거래·일정·기록·사진 집계. */
    @GetMapping("/{id}/summary")
    public ApiResponse<CollectionSummaryResponse> summary(@PathVariable Long id) {
        return ApiResponse.ok(collectionService.summary(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CollectionResponse> create(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.ok(collectionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CollectionResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody CollectionRequest request) {
        return ApiResponse.ok(collectionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        collectionService.delete(id);
        return ApiResponse.ok();
    }
}
