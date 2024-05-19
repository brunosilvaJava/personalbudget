package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
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
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<FinancialMovementResponse>> find(@RequestParam(defaultValue = "")
                                                                String description,
                                                                @RequestParam(value = "operation_type", required = false)
                                                                List<OperationType> operationTypes,
                                                                @RequestParam(value = "status", required = false)
                                                                List<FinancialMovementStatus> statuses,
                                                                @RequestParam("start_date")
                                                                LocalDate startDate,
                                                                @RequestParam("end_date")
                                                                LocalDate endDate) {
        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}",
                description, operationTypes, statuses, startDate, endDate);
        return ResponseEntity.ok(mapper.toResponse(service.find(description, operationTypes, statuses, startDate, endDate)));
    }

}
