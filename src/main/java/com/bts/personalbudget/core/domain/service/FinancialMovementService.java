package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.repository.FinancialMovementRepository;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class FinancialMovementService {

    private final FinancialMovementRepository repository;
    private final FinancialMovementMapper mapper;

    public void save(final FinancialMovement financialMovement) {
        log.info("m=create, financialMovement={}", financialMovement);
        repository.save(mapper.toModel(financialMovement));
    }

}
