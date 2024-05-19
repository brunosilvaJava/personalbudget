package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovementEntityFactory;
import com.bts.personalbudget.core.domain.repository.FinancialMovementRepository;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialMovementServiceTest {

    @InjectMocks
    private FinancialMovementService financialMovementService;

    @Mock
    private FinancialMovementRepository repository;

    @Spy
    private FinancialMovementMapper mapper;

    @Test
    void shouldFindFinancialMovements() {

        final String description = "";
        final List<OperationType> operationTypes = null;
        final List<FinancialMovementStatus> statuses = null;
        final LocalDate startDate = LocalDate.now();
        final LocalDate endDate = LocalDate.now();

        when(repository.findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetween(
                        description, Arrays.stream(OperationType.values()).toList(),
                Arrays.stream(FinancialMovementStatus.values()).toList(),
                startDate.atStartOfDay(), endDate.atStartOfDay().plusDays(1)))
                .thenReturn(Optional.of(List.of(FinancialMovementEntityFactory.build())));

        final List<FinancialMovement> financialMovements =
                financialMovementService.find(description, operationTypes, statuses, startDate, endDate);

        assertNotNull(financialMovements);

    }

}
