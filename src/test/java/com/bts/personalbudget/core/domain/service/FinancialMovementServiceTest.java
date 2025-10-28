package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT_PAID;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DESCRIPTION;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.MOVEMENT_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DUE_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.OPERATION_TYPE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.PAY_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialMovementServiceTest {

    @InjectMocks
    private FinancialMovementService financialMovementService;

    @Mock
    private FinancialMovementRepository repository;

    @Spy
    private FinancialMovementMapper mapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldCreateFinancialMovement() {
        Map<FinancialMovementProperty, String> data = new HashMap<>(FinancialMovementFactory.data());
        FinancialMovement financialMovement = FinancialMovementFactory.buildModel(data);
        financialMovementService.create(financialMovement);
        verify(repository).save(FinancialMovementFactory.buildEntity(data));
    }

//    @Test
//    void shouldFindFinancialMovements() {
//
//        final String description = "";
//        final List<OperationType> operationTypes = null;
//        final List<FinancialMovementStatus> statuses = null;
//        final LocalDate startDate = LocalDate.now();
//        final LocalDate endDate = LocalDate.now();
//
//        when(repository.findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
//                description, Arrays.stream(OperationType.values()).toList(),
//                Arrays.stream(FinancialMovementStatus.values()).toList(),
//                startDate.atStartOfDay(), endDate.atStartOfDay().plusDays(1)))
//                .thenReturn(Optional.of(List.of(FinancialMovementFactory.buildEntity())));
//
//        final List<FinancialMovement> financialMovements =
//                financialMovementService.find(description, operationTypes, statuses, startDate, endDate, null);
//
//        assertNotNull(financialMovements);
//        assertFalse(financialMovements.isEmpty());
//    }

    @Test
    void shouldFindFinancialMovement() throws NotFoundException {
        final UUID code = UUID.randomUUID();
        when(repository.findByCode(code)).thenReturn(Optional.of(FinancialMovementFactory.buildEntity()));
        final FinancialMovement financialMovement = financialMovementService.find(code);
        assertNotNull(financialMovement);
    }

    @Test
    void shouldUpdateFinancialMovement() throws NotFoundException {
        String description = "descricao alterada";
        OperationType operationType = OperationType.CREDIT;
        BigDecimal amount = BigDecimal.valueOf(100L);
        LocalDateTime now = LocalDateTime.now();
        FinancialMovementStatus status = FinancialMovementStatus.PAID_OUT;
        Map<FinancialMovementProperty, String> data = new HashMap<>(FinancialMovementFactory.data());

        data.put(OPERATION_TYPE, operationType.name());
        data.put(DESCRIPTION, description);
        data.put(AMOUNT, amount.toString());
        data.put(AMOUNT_PAID, amount.toString());
        data.put(MOVEMENT_DATE, now.toString());
        data.put(DUE_DATE, now.toString());
        data.put(PAY_DATE, now.toString());
        data.put(STATUS, status.name());

        FinancialMovement financialMovementToUpdate = FinancialMovementFactory.buildModel(data);

        when(repository.findByCode(financialMovementToUpdate.code())).thenReturn(Optional.of(FinancialMovementFactory.buildEntity()));

        FinancialMovement updatedFinancialMovement = financialMovementService.update(financialMovementToUpdate);

        assertEquals(operationType, updatedFinancialMovement.operationType());
        assertEquals(description, updatedFinancialMovement.description());
        assertEquals(amount, updatedFinancialMovement.amount());
        assertEquals(amount, updatedFinancialMovement.amountPaid());
        assertEquals(now, updatedFinancialMovement.movementDate());
        assertEquals(now, updatedFinancialMovement.dueDate());
        assertEquals(now, updatedFinancialMovement.payDate());
        assertEquals(status, updatedFinancialMovement.status());
    }

    @Test
    void shouldDeleteFinancialMovement() throws NotFoundException {
        FinancialMovementEntity financialMovementEntity = FinancialMovementFactory.buildEntity();
        when(repository.findByCode(financialMovementEntity.getCode())).thenReturn(Optional.of(financialMovementEntity));
        financialMovementService.delete(financialMovementEntity.getCode());
        assertFalse(financialMovementEntity.getFlagActive());
    }
}
