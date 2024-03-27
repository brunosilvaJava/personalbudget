package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
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
    public List<FinancialMovementModel> get() {
        return repository.findAll();
    }

    @PostMapping
    public void post(@RequestBody FinancialMovementModel model) {
        repository.save(model);
    }

}
