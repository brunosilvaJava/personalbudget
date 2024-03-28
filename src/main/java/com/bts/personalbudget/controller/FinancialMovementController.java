package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.model.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.repository.FinancialMovementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class FinancialMovementController {

    private final FinancialMovementRepository repository;

    @GetMapping
    public List<FinancialMovementEntity> get() {
        return repository.findAll();
    }

    @PostMapping
    public void post(@RequestBody FinancialMovementEntity model) {
        repository.save(model);
    }

}
