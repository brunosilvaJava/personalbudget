package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/financial_movement")
@RestController
public class FinancialMovementController {

    private final FinancialMovementService service;
    private final FinancialMovementMapper mapper;

    @PostMapping
    public ResponseEntity create(@RequestBody @Valid final FinancialMovementRequest request) {
        log.info("m=create, request={}", request);
        service.save(mapper.toEntity(request));
        return ResponseEntity.ok().build();
    }

}
