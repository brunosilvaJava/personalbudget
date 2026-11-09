package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.financialmovement.FinancialMovementUpdateRequest;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMovementMapperCompetenceTest {

    private final FinancialMovementMapper mapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldMapCompetenceBetweenEntityModelAndResponse() {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setCompetenceDate(LocalDate.of(2026, 10, 1));
        entity.setCompetenceOverridden(true);

        final FinancialMovement model = mapper.toModel(entity);
        final FinancialMovementEntity mappedBack = mapper.toEntity(model);

        assertThat(model.competenceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(model.competenceOverridden()).isTrue();
        assertThat(mapper.toResponse(model).competenceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(mapper.toResponse(model).competenceOverridden()).isTrue();
        assertThat(mappedBack.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(mappedBack.getCompetenceOverridden()).isTrue();
    }

    @Test
    void shouldMapUpdateRequestToUpdateCommand() {
        final UUID code = UUID.randomUUID();
        final LocalDate competence = LocalDate.of(2026, 10, 1);

        final FinancialMovementUpdate manual = mapper.toUpdate(new FinancialMovementUpdateRequest(
                null, null, null, null, null, null, null, null, null, competence, null), code);
        final FinancialMovementUpdate restore = mapper.toUpdate(new FinancialMovementUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, true), code);
        final FinancialMovementUpdate omitted = mapper.toUpdate(new FinancialMovementUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, null), code);

        assertThat(manual.code()).isEqualTo(code);
        assertThat(manual.competenceDate()).isEqualTo(competence);
        assertThat(manual.restoreAutomaticCompetence()).isFalse();
        assertThat(restore.competenceDate()).isNull();
        assertThat(restore.restoreAutomaticCompetence()).isTrue();
        assertThat(omitted.competenceDate()).isNull();
        assertThat(omitted.restoreAutomaticCompetence()).isFalse();
    }

    @Test
    void shouldCarryOccurrenceDateOfFixedBillAndLeaveCompetenceToTheService() {
        final FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, Set.of(10));

        final FinancialMovement movement =
                mapper.toFinancialMovement(fixedBill, FinancialMovementStatus.PENDING);

        assertThat(movement.occurrenceDate()).isEqualTo(fixedBill.getNextDueDate());
        assertThat(movement.competenceDate()).isNull();
        assertThat(movement.competenceOverridden()).isFalse();
    }

    @Test
    void shouldCarryMovementAndDueDatesOfFixedBill() {
        final FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, Set.of(10));
        fixedBill.setNextDueDate(java.time.LocalDate.of(2026, 11, 10));

        final FinancialMovement movement =
                mapper.toFinancialMovement(fixedBill, FinancialMovementStatus.PENDING);

        assertThat(movement.movementDate()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 10, 0, 0));
        assertThat(movement.dueDate()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 10, 0, 0));
        assertThat(movement.recurrenceBillCode()).isEqualTo(fixedBill.getCode());
        assertThat(movement.occurrenceDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 10));
        assertThat(movement.status()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(movement.flagActive()).isTrue();
        assertThat(movement.amount()).isEqualByComparingTo(fixedBill.getAmount().negate());
    }

    @Test
    void shouldCarryOccurrenceDateOfInstallmentAndLeaveCompetenceToTheService() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel();

        final FinancialMovement movement =
                mapper.toFinancialMovement(installmentBill, FinancialMovementStatus.PENDING);

        assertThat(movement.operationType()).isEqualTo(OperationType.DEBIT);
        assertThat(movement.occurrenceDate()).isEqualTo(installmentBill.getNextInstallmentDate());
        assertThat(movement.competenceDate()).isNull();
    }

    @Test
    void shouldCarryPurchaseAndInstallmentDatesOfInstallmentBill() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel();

        final FinancialMovement movement =
                mapper.toFinancialMovement(installmentBill, FinancialMovementStatus.PENDING);

        assertThat(movement.movementDate()).isEqualTo(installmentBill.getPurchaseDate().atStartOfDay());
        assertThat(movement.dueDate()).isEqualTo(installmentBill.getNextInstallmentDate().atStartOfDay());
        assertThat(movement.recurrenceBillCode()).isEqualTo(installmentBill.getCode());
        assertThat(movement.flagActive()).isTrue();
    }
}
