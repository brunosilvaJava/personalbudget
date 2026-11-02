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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityNotFoundException;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void shouldFindFinancialMovement() {
        final UUID code = UUID.randomUUID();
        when(repository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(FinancialMovementFactory.buildEntity()));
        final FinancialMovement financialMovement = financialMovementService.find(code);
        assertNotNull(financialMovement);
    }

    @Test
    void shouldUpdateFinancialMovement() {
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

        when(repository.findByCodeAndFlagActiveTrue(financialMovementToUpdate.code())).thenReturn(Optional.of(FinancialMovementFactory.buildEntity()));

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
    void shouldDeleteFinancialMovement() {
        FinancialMovementEntity financialMovementEntity = FinancialMovementFactory.buildEntity();
        when(repository.findByCodeAndFlagActiveTrue(financialMovementEntity.getCode())).thenReturn(Optional.of(financialMovementEntity));
        financialMovementService.delete(financialMovementEntity.getCode());
        assertFalse(financialMovementEntity.getFlagActive());
    }

    @Test
    void shouldKeepOccurrenceDateWhenDueDateIsUpdated() {
        LocalDate originalOccurrenceDate = LocalDate.of(2026, 11, 10);
        FinancialMovementEntity financialMovementEntity = FinancialMovementFactory.buildEntity();
        financialMovementEntity.setOccurrenceDate(originalOccurrenceDate);

        LocalDateTime newDueDate = LocalDateTime.of(2026, 11, 20, 0, 0);
        Map<FinancialMovementProperty, String> data = new HashMap<>(FinancialMovementFactory.data());
        data.put(DUE_DATE, newDueDate.toString());
        FinancialMovement financialMovementToUpdate = FinancialMovementFactory.buildModel(data);

        when(repository.findByCodeAndFlagActiveTrue(financialMovementToUpdate.code())).thenReturn(Optional.of(financialMovementEntity));

        financialMovementService.update(financialMovementToUpdate);

        // occurrence_date is not part of FinancialMovementUpdateRequest and the update() method
        // never touches it: it is set once, at generation time, and stays immutable.
        assertEquals(newDueDate, financialMovementEntity.getDueDate());
        assertEquals(originalOccurrenceDate, financialMovementEntity.getOccurrenceDate());
    }

    @Test
    void shouldStoreNegativeAmountsWhenDebitIsCreatedAsPaidOut() {
        final LocalDateTime now = LocalDateTime.now();
        final FinancialMovement financialMovement = new FinancialMovement(
                UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"), new BigDecimal("12.50"),
                now, now, now, FinancialMovementStatus.PAID_OUT, Boolean.TRUE, null, null);

        financialMovementService.create(financialMovement);

        final ArgumentCaptor<FinancialMovementEntity> captor = ArgumentCaptor.forClass(FinancialMovementEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-12.50");
        assertThat(captor.getValue().getAmountPaid()).isEqualByComparingTo("-12.50");
    }

    @Test
    void shouldStoreNegativeAmountPaidWhenDebitIsPaidOutViaPatch() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        final FinancialMovement payment = new FinancialMovement(
                entity.getCode(), null, null, null, new BigDecimal("30.00"),
                null, null, LocalDateTime.now(), FinancialMovementStatus.PAID_OUT, null, null, null);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(payment);

        assertEquals(FinancialMovementStatus.PAID_OUT, entity.getStatus());
        assertThat(entity.getAmountPaid()).isEqualByComparingTo("-30.00");
        assertThat(entity.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldStoreNegativeAmountWhenOnlyOperationTypeChangesToDebit() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.CREDIT, new BigDecimal("30.00"));
        final FinancialMovement change = onlyOperationType(entity.getCode(), OperationType.DEBIT);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(change);

        assertEquals(OperationType.DEBIT, entity.getOperationType());
        assertThat(entity.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldStorePositiveAmountWhenOnlyOperationTypeChangesToCredit() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        final FinancialMovement change = onlyOperationType(entity.getCode(), OperationType.CREDIT);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(change);

        assertEquals(OperationType.CREDIT, entity.getOperationType());
        assertThat(entity.getAmount()).isEqualByComparingTo("30.00");
    }

    private static FinancialMovementEntity pendingEntity(final OperationType operationType, final BigDecimal amount) {
        final LocalDateTime now = LocalDateTime.now();
        final FinancialMovementEntity entity = FinancialMovementEntity.builder()
                .code(UUID.randomUUID())
                .operationType(operationType)
                .description("Teste")
                .amount(amount)
                .movementDate(now)
                .dueDate(now)
                .status(FinancialMovementStatus.PENDING)
                .build();
        entity.prePersist();
        return entity;
    }

    private static FinancialMovement onlyOperationType(final UUID code, final OperationType operationType) {
        return new FinancialMovement(code, operationType, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void shouldReturnCreatedMovementWhenCreated() {
        final FinancialMovement financialMovement = FinancialMovementFactory.buildModel();
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final FinancialMovement created = financialMovementService.create(financialMovement);

        assertNotNull(created);
        assertEquals(financialMovement.code(), created.code());
    }

    @Test
    void shouldThrowEntityNotFoundWhenCodeDoesNotExist() {
        final UUID code = UUID.randomUUID();
        when(repository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> financialMovementService.find(code));
    }
}
