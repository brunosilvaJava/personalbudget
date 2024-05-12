package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/financial_movement")
@RestController
public class FinancialMovementController {

    private final FinancialMovementService service;
    private final FinancialMovementMapper mapper;

    @PostMapping
    public void post(@RequestBody @Valid final FinancialMovementRequest request) {
        service.save(mapper.toEntity(request));
    }

}
