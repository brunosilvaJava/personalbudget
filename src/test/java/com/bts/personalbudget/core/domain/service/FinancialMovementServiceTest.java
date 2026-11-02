package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.exception.InvalidFieldsException;
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

import static com.bts.personalbudget.core.domain.factory.CategoryFactory.CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.OTHER_CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT_PAID;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DESCRIPTION;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.MOVEMENT_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DUE_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.OPERATION_TYPE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.PAY_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.STATUS;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.toUpdate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialMovementServiceTest {

    @InjectMocks
    private FinancialMovementService financialMovementService;

    @Mock
    private FinancialMovementRepository repository;

    @Mock
    private CategoryService categoryService;

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

        FinancialMovement updatedFinancialMovement = financialMovementService.update(toUpdate(financialMovementToUpdate));

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

        financialMovementService.update(toUpdate(financialMovementToUpdate));

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
                now, now, now, FinancialMovementStatus.PAID_OUT, Boolean.TRUE, null, null, null);

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
                null, null, LocalDateTime.now(), FinancialMovementStatus.PAID_OUT, null, null, null, null);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(payment));

        assertEquals(FinancialMovementStatus.PAID_OUT, entity.getStatus());
        assertThat(entity.getAmountPaid()).isEqualByComparingTo("-30.00");
        assertThat(entity.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldStoreNegativeAmountWhenOnlyOperationTypeChangesToDebit() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.CREDIT, new BigDecimal("30.00"));
        final FinancialMovement change = onlyOperationType(entity.getCode(), OperationType.DEBIT);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(change));

        assertEquals(OperationType.DEBIT, entity.getOperationType());
        assertThat(entity.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldStorePositiveAmountWhenOnlyOperationTypeChangesToCredit() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        final FinancialMovement change = onlyOperationType(entity.getCode(), OperationType.CREDIT);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(change));

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
                null, null, null, null, null, null, null, null);
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

    @Test
    void shouldCreateMovementWithCategory() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final FinancialMovement created = financialMovementService.create(
                pendingMovement(OperationType.DEBIT, null, CATEGORY_CODE));

        verify(categoryService).validateForUse(CATEGORY_CODE, OperationType.DEBIT);
        assertEquals(CATEGORY_CODE, created.categoryCode());
    }

    @Test
    void shouldCreateMovementWithoutCategoryAndSkipValidation() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final FinancialMovement created = financialMovementService.create(
                pendingMovement(OperationType.DEBIT, null, null));

        verifyNoInteractions(categoryService);
        assertThat(created.categoryCode()).isNull();
    }

    @Test
    void shouldRejectMovementWhenCategoryIsInactive() {
        doThrow(categoryRejection("categoria inativa"))
                .when(categoryService).validateForUse(CATEGORY_CODE, OperationType.DEBIT);

        assertThrows(InvalidFieldsException.class, () -> financialMovementService.create(
                pendingMovement(OperationType.DEBIT, null, CATEGORY_CODE)));

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectMovementWhenCategoryTypeDiffersFromOperationType() {
        doThrow(categoryRejection("categoria é do tipo DEBIT e o lançamento é CREDIT"))
                .when(categoryService).validateForUse(CATEGORY_CODE, OperationType.CREDIT);

        assertThrows(InvalidFieldsException.class, () -> financialMovementService.create(
                pendingMovement(OperationType.CREDIT, null, CATEGORY_CODE)));

        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipCategoryValidationWhenMovementComesFromRecurrenceBill() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final FinancialMovement created = financialMovementService.create(
                pendingMovement(OperationType.DEBIT, UUID.randomUUID(), CATEGORY_CODE));

        verifyNoInteractions(categoryService);
        assertEquals(CATEGORY_CODE, created.categoryCode());
    }

    @Test
    void shouldReplaceCategoryOnUpdate() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        entity.setCategoryCode(CATEGORY_CODE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(onlyCategory(entity.getCode(), OTHER_CATEGORY_CODE)));

        verify(categoryService).validateForUse(OTHER_CATEGORY_CODE, OperationType.DEBIT);
        assertEquals(OTHER_CATEGORY_CODE, entity.getCategoryCode());
    }

    @Test
    void shouldKeepCategoryWhenUpdateRequestHasNoCategory() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        entity.setCategoryCode(CATEGORY_CODE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(new FinancialMovement(entity.getCode(), null, "Novo nome", null, null,
                null, null, null, null, null, null, null, null)));

        verifyNoInteractions(categoryService);
        assertEquals("Novo nome", entity.getDescription());
        assertEquals(CATEGORY_CODE, entity.getCategoryCode());
    }

    @Test
    void shouldRejectUpdateWhenOperationTypeChangesAndCategoryNoLongerMatches() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        entity.setCategoryCode(CATEGORY_CODE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));
        doThrow(categoryRejection("categoria é do tipo DEBIT e o lançamento é CREDIT"))
                .when(categoryService).validateForUse(CATEGORY_CODE, OperationType.CREDIT);

        assertThrows(InvalidFieldsException.class,
                () -> financialMovementService.update(toUpdate(onlyOperationType(entity.getCode(), OperationType.CREDIT))));

        assertEquals(OperationType.DEBIT, entity.getOperationType());
        assertThat(entity.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldNotValidateCategoryWhenOperationTypeInUpdateIsUnchanged() {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        entity.setCategoryCode(CATEGORY_CODE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(toUpdate(onlyOperationType(entity.getCode(), OperationType.DEBIT)));

        verifyNoInteractions(categoryService);
        assertEquals(CATEGORY_CODE, entity.getCategoryCode());
    }

    private static FinancialMovement pendingMovement(final OperationType operationType,
                                                     final UUID recurrenceBillCode,
                                                     final UUID categoryCode) {
        final LocalDateTime now = LocalDateTime.now();
        return new FinancialMovement(UUID.randomUUID(), operationType, "Aluguel", new BigDecimal("100.00"), null,
                now, now, null, FinancialMovementStatus.PENDING, Boolean.TRUE, recurrenceBillCode, null,
                categoryCode);
    }

    private static FinancialMovement onlyCategory(final UUID code, final UUID categoryCode) {
        return new FinancialMovement(code, null, null, null, null,
                null, null, null, null, null, null, null, categoryCode);
    }

    private static InvalidFieldsException categoryRejection(final String message) {
        return new InvalidFieldsException("Campos inválidos", CategoryService.class,
                Map.of("categoryCode", message));
    }

    private static final LocalDate OCCURRENCE = LocalDate.of(2026, 11, 10);
    private static final LocalDate MANUAL_COMPETENCE = LocalDate.of(2026, 10, 1);

    @Test
    void shouldSetAutomaticCompetenceFromMovementDateWhenStandaloneMovementIsCreated() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final LocalDateTime movementDate = LocalDateTime.of(2026, 11, 5, 18, 30);

        final FinancialMovement created = financialMovementService.create(
                standalone(movementDate, null));

        assertEquals(LocalDate.of(2026, 11, 5), created.competenceDate());
        assertFalse(created.competenceOverridden());
    }

    @Test
    void shouldSetAutomaticCompetenceFromOccurrenceDateWhenMovementComesFromRecurrenceBill() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final LocalDateTime dueDate = OCCURRENCE.atStartOfDay();

        final FinancialMovement created = financialMovementService.create(new FinancialMovement(
                OperationType.DEBIT, "Aluguel", new BigDecimal("100.00"), dueDate.minusDays(3), dueDate,
                FinancialMovementStatus.PENDING, UUID.randomUUID(), OCCURRENCE));

        assertEquals(OCCURRENCE, created.competenceDate());
        assertEquals(OCCURRENCE, created.occurrenceDate());
        assertFalse(created.competenceOverridden());
    }

    @Test
    void shouldCreateMovementWithManualCompetenceWhenClientInformsIt() {
        when(repository.save(any(FinancialMovementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final FinancialMovement created = financialMovementService.create(
                standalone(LocalDateTime.of(2026, 11, 5, 10, 0), MANUAL_COMPETENCE));

        assertEquals(MANUAL_COMPETENCE, created.competenceDate());
        assertTrue(created.competenceOverridden());
    }

    @Test
    void shouldKeepCompetenceWhenPatchOmitsCompetence() {
        final FinancialMovementEntity entity = entityWithCompetence(MANUAL_COMPETENCE, true);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(patch(entity.getCode(), null, null, null, null, false));

        assertEquals(MANUAL_COMPETENCE, entity.getCompetenceDate());
        assertTrue(entity.getCompetenceOverridden());
    }

    @Test
    void shouldRecalculateAutomaticCompetenceWhenMovementDateChanges() {
        final FinancialMovementEntity entity = entityWithCompetence(LocalDate.of(2026, 11, 5), false);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(
                patch(entity.getCode(), LocalDateTime.of(2026, 12, 2, 9, 0), null, null, null, false));

        assertEquals(LocalDate.of(2026, 12, 2), entity.getCompetenceDate());
        assertFalse(entity.getCompetenceOverridden());
    }

    @Test
    void shouldNotOverwriteManualCompetenceWhenMovementDateChanges() {
        final FinancialMovementEntity entity = entityWithCompetence(MANUAL_COMPETENCE, true);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(
                patch(entity.getCode(), LocalDateTime.of(2026, 12, 2, 9, 0), null, null, null, false));

        assertEquals(MANUAL_COMPETENCE, entity.getCompetenceDate());
        assertTrue(entity.getCompetenceOverridden());
    }

    @Test
    void shouldNotChangeCompetenceWhenDueDateAndPayDateChange() {
        final FinancialMovementEntity automatic = entityWithCompetence(LocalDate.of(2026, 11, 5), false);
        final FinancialMovementEntity manual = entityWithCompetence(MANUAL_COMPETENCE, true);
        when(repository.findByCodeAndFlagActiveTrue(automatic.getCode())).thenReturn(Optional.of(automatic));
        when(repository.findByCodeAndFlagActiveTrue(manual.getCode())).thenReturn(Optional.of(manual));
        final LocalDateTime newDate = LocalDateTime.of(2027, 3, 20, 0, 0);

        financialMovementService.update(patch(automatic.getCode(), null, newDate, newDate, null, false));
        financialMovementService.update(patch(manual.getCode(), null, newDate, newDate, null, false));

        assertEquals(newDate, automatic.getDueDate());
        assertEquals(newDate, automatic.getPayDate());
        assertEquals(LocalDate.of(2026, 11, 5), automatic.getCompetenceDate());
        assertEquals(MANUAL_COMPETENCE, manual.getCompetenceDate());
    }

    @Test
    void shouldSetManualCompetenceWhenPatchInformsIt() {
        final FinancialMovementEntity entity = entityWithCompetence(LocalDate.of(2026, 11, 5), false);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(patch(entity.getCode(), null, null, null, MANUAL_COMPETENCE, false));

        assertEquals(MANUAL_COMPETENCE, entity.getCompetenceDate());
        assertTrue(entity.getCompetenceOverridden());
    }

    @Test
    void shouldRestoreAutomaticCompetenceFromMovementDateWhenRequested() {
        final FinancialMovementEntity entity = entityWithCompetence(MANUAL_COMPETENCE, true);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(patch(entity.getCode(), null, null, null, null, true));

        assertEquals(entity.getMovementDate().toLocalDate(), entity.getCompetenceDate());
        assertFalse(entity.getCompetenceOverridden());
    }

    @Test
    void shouldRestoreAutomaticCompetenceFromOccurrenceDateWhenMovementIsRecurring() {
        final FinancialMovementEntity entity = entityWithCompetence(MANUAL_COMPETENCE, true);
        entity.setRecurrenceBillCode(UUID.randomUUID());
        entity.setOccurrenceDate(OCCURRENCE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(patch(entity.getCode(), null, null, null, null, true));

        assertEquals(OCCURRENCE, entity.getCompetenceDate());
        assertEquals(OCCURRENCE, entity.getOccurrenceDate());
        assertFalse(entity.getCompetenceOverridden());
    }

    @Test
    void shouldKeepAutomaticCompetenceOfRecurringMovementWhenMovementDateChanges() {
        final FinancialMovementEntity entity = entityWithCompetence(OCCURRENCE, false);
        entity.setRecurrenceBillCode(UUID.randomUUID());
        entity.setOccurrenceDate(OCCURRENCE);
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(
                patch(entity.getCode(), LocalDateTime.of(2027, 1, 1, 0, 0), null, null, null, false));

        assertEquals(OCCURRENCE, entity.getCompetenceDate());
    }

    @Test
    void shouldRejectRestoreWhenLegacyRecurringMovementHasNoOccurrenceDate() {
        final FinancialMovementEntity entity = entityWithCompetence(null, false);
        entity.setRecurrenceBillCode(UUID.randomUUID());
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        assertThrows(InvalidFieldsException.class,
                () -> financialMovementService.update(patch(entity.getCode(), null, null, null, null, true)));
        assertThat(entity.getCompetenceDate()).isNull();
    }

    @Test
    void shouldLeaveCompetenceNullWhenLegacyRecurringMovementWithoutOccurrenceDateIsUpdated() {
        final FinancialMovementEntity entity = entityWithCompetence(null, false);
        entity.setRecurrenceBillCode(UUID.randomUUID());
        when(repository.findByCodeAndFlagActiveTrue(entity.getCode())).thenReturn(Optional.of(entity));

        financialMovementService.update(
                patch(entity.getCode(), LocalDateTime.of(2027, 1, 1, 0, 0), null, null, null, false));

        assertThat(entity.getCompetenceDate()).isNull();
        assertThat(entity.getOccurrenceDate()).isNull();
    }

    @Test
    void shouldRejectUpdateWithManualCompetenceAndRestoreTogether() {
        assertThrows(InvalidFieldsException.class,
                () -> patch(UUID.randomUUID(), null, null, null, MANUAL_COMPETENCE, true));
    }

    private static FinancialMovement standalone(final LocalDateTime movementDate, final LocalDate competenceDate) {
        return new FinancialMovement(UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"),
                null, movementDate, movementDate, null, FinancialMovementStatus.PENDING, Boolean.TRUE, null, null,
                null, competenceDate, false);
    }

    private static FinancialMovementEntity entityWithCompetence(final LocalDate competenceDate, final boolean overridden) {
        final FinancialMovementEntity entity = pendingEntity(OperationType.DEBIT, new BigDecimal("-30.00"));
        entity.setMovementDate(LocalDateTime.of(2026, 11, 5, 10, 0));
        entity.setDueDate(LocalDateTime.of(2026, 11, 20, 0, 0));
        entity.setCompetenceDate(competenceDate);
        entity.setCompetenceOverridden(overridden);
        return entity;
    }

    private static FinancialMovementUpdate patch(final UUID code, final LocalDateTime movementDate,
                                                 final LocalDateTime dueDate, final LocalDateTime payDate,
                                                 final LocalDate competenceDate, final boolean restore) {
        return new FinancialMovementUpdate(code, null, null, null, null, movementDate, dueDate, payDate,
                null, null, competenceDate, restore);
    }

}
