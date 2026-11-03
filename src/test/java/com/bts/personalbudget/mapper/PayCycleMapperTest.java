package com.bts.personalbudget.mapper;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.CONFIG_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildTransition;
import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleConfigResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleIncomeImpactResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleMovementImpactResponse;
import com.bts.personalbudget.controller.paycycle.PayCyclePendingResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleActivationPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustment;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustmentPlanner;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentResult;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleMapperTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final YearMonth DEC = YearMonth.of(2026, 12);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);

    private final PayCycleMapper mapper = PayCycleMapper.INSTANCE;
    private final IncomeScheduleEntity scheduleEntity = IncomeScheduleEntity.builder().id(1L).code(SCHEDULE_CODE).build();

    // ---- entity -> model

    @Test
    void shouldMapAnEntityToTheModelTurningTheFirstDayIntoTheMonth() {
        final UUID code = UUID.randomUUID();
        final PayCycleEntity entity = PayCycleEntity.builder()
                .id(10L).code(code).startDate(NOV_5).endDate(DEC_7).kind(PayCycleKind.REGULAR)
                .anchorSchedule(scheduleEntity).anchorNominalMonth(NOV.atDay(1))
                .lifecycle(PayCycleLifecycle.ACTIVE).startAdjusted(false).endAdjusted(true).version(4L)
                .build();

        final PayCycle cycle = mapper.toModel(entity);

        assertThat(cycle.code()).isEqualTo(code);
        assertThat(cycle.startDate()).isEqualTo(NOV_5);
        assertThat(cycle.endDate()).isEqualTo(DEC_7);
        assertThat(cycle.kind()).isEqualTo(PayCycleKind.REGULAR);
        assertThat(cycle.anchorScheduleCode()).isEqualTo(SCHEDULE_CODE);
        assertThat(cycle.anchorNominalMonth()).isEqualTo(NOV);
        assertThat(cycle.lifecycle()).isEqualTo(PayCycleLifecycle.ACTIVE);
        assertThat(cycle.startAdjusted()).isFalse();
        assertThat(cycle.endAdjusted()).isTrue();
        assertThat(cycle.version()).isEqualTo(4L);
    }

    @Test
    void shouldMapATransitionWithoutAnchorToANullScheduleAndMonth() {
        final PayCycleEntity entity = PayCycleEntity.builder()
                .code(UUID.randomUUID()).startDate(NOV_5).endDate(DEC_7).kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE).startAdjusted(false).endAdjusted(false).version(0L)
                .build();

        final PayCycle cycle = mapper.toModel(entity);

        assertThat(cycle.anchorScheduleCode()).isNull();
        assertThat(cycle.anchorNominalMonth()).isNull();
        assertThat(cycle.kind()).isEqualTo(PayCycleKind.TRANSITION);
    }

    @Test
    void shouldTreatNullAdjustmentFlagsAsNotAdjusted() {
        final PayCycleEntity entity = PayCycleEntity.builder()
                .code(UUID.randomUUID()).startDate(NOV_5).endDate(DEC_7).kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE).version(0L)
                .build();

        final PayCycle cycle = mapper.toModel(entity);

        assertThat(cycle.startAdjusted()).isFalse();
        assertThat(cycle.endAdjusted()).isFalse();
    }

    @Test
    void shouldMapAListOfEntitiesKeepingTheOrder() {
        final PayCycleEntity first = PayCycleEntity.builder()
                .code(UUID.randomUUID()).startDate(NOV_5).endDate(DEC_7).kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE).startAdjusted(false).endAdjusted(false).version(0L).build();
        final PayCycleEntity second = PayCycleEntity.builder()
                .code(UUID.randomUUID()).startDate(DEC_7).endDate(JAN_5).kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE).startAdjusted(false).endAdjusted(false).version(0L).build();

        final List<PayCycle> cycles = mapper.toModelList(List.of(first, second));

        assertThat(cycles).extracting(PayCycle::startDate).containsExactly(NOV_5, DEC_7);
    }

    // ---- model -> entity

    @Test
    void shouldMapANewCycleToAnEntityWithoutIdOrVersion() {
        final PayCycle cycle = buildCycle(NOV_5, DEC_7, NOV);

        final PayCycleEntity entity = mapper.toEntity(cycle, scheduleEntity);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCode()).isEqualTo(cycle.code());
        assertThat(entity.getStartDate()).isEqualTo(NOV_5);
        assertThat(entity.getEndDate()).isEqualTo(DEC_7);
        assertThat(entity.getKind()).isEqualTo(PayCycleKind.REGULAR);
        assertThat(entity.getAnchorSchedule()).isSameAs(scheduleEntity);
        assertThat(entity.getAnchorNominalMonth()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(entity.getLifecycle()).isEqualTo(PayCycleLifecycle.ACTIVE);
        assertThat(entity.getStartAdjusted()).isFalse();
        assertThat(entity.getEndAdjusted()).isFalse();
    }

    @Test
    void shouldMapATransitionToAnEntityWithoutAnchor() {
        final PayCycle cycle = buildTransition(NOV_5, DEC_7);

        final PayCycleEntity entity = mapper.toEntity(cycle, null);

        assertThat(entity.getAnchorSchedule()).isNull();
        assertThat(entity.getAnchorNominalMonth()).isNull();
        assertThat(entity.getKind()).isEqualTo(PayCycleKind.TRANSITION);
    }

    @Test
    void shouldConvertMonthsToTheFirstDayAndBackKeepingNull() {
        assertThat(mapper.toDate(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(mapper.toDate(null)).isNull();
        assertThat(mapper.toYearMonth(LocalDate.of(2027, 2, 1))).isEqualTo(YearMonth.of(2027, 2));
        assertThat(mapper.toYearMonth(null)).isNull();
    }

    // ---- configuration

    @Test
    void shouldMapTheConfigurationInCalendarMonthMode() {
        final PayCycleConfigEntity entity = PayCycleConfigEntity.builder()
                .id(1L).code(CONFIG_CODE).mode(PayCycleMode.CALENDAR_MONTH).version(0L).build();

        assertThat(mapper.toModel(entity)).isEqualTo(buildCalendarMonthConfig());
    }

    @Test
    void shouldMapTheConfigurationInPayCycleModeWithTheAnchorCode() {
        final PayCycleConfigEntity entity = PayCycleConfigEntity.builder()
                .id(1L).code(CONFIG_CODE).mode(PayCycleMode.PAY_CYCLE).anchorSchedule(scheduleEntity)
                .activationDate(NOV_5).version(2L).build();

        assertThat(mapper.toModel(entity)).isEqualTo(buildPayCycleConfig(NOV_5, 2L));
    }

    // ---- model -> response

    @Test
    void shouldMapACycleViewToTheResponseWithTheDerivedFields() {
        final PayCycle cycle = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleView view = new PayCycleView(cycle, PayCycleStatus.OPEN, List.of());

        final PayCycleResponse response = mapper.toResponse(view);

        assertThat(response.code()).isEqualTo(cycle.code());
        assertThat(response.startDate()).isEqualTo(NOV_5);
        assertThat(response.endDateExclusive()).isEqualTo(DEC_7);
        assertThat(response.lastDay()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(response.totalDays()).isEqualTo(32);
        assertThat(response.label()).isEqualTo("Nov/Dez");
        assertThat(response.referenceMonth()).isEqualTo(NOV);
        assertThat(response.kind()).isEqualTo(PayCycleKind.REGULAR);
        assertThat(response.status()).isEqualTo(PayCycleStatus.OPEN);
        assertThat(response.anchorScheduleCode()).isEqualTo(SCHEDULE_CODE);
        assertThat(response.startAdjusted()).isFalse();
        assertThat(response.endAdjusted()).isFalse();
        assertThat(response.version()).isEqualTo(0L);
        assertThat(response.pending()).isEmpty();
    }

    @Test
    void shouldLabelACycleThatCrossesTheYearWithBothYears() {
        final PayCycleView view = new PayCycleView(buildCycle(DEC_7, JAN_5, DEC), PayCycleStatus.PLANNED, List.of());

        assertThat(mapper.toResponse(view).label()).isEqualTo("Dez/2026–Jan/2027");
    }

    @Test
    void shouldMapATransitionWithoutReferenceMonthOrSchedule() {
        final PayCycleView view = new PayCycleView(buildTransition(NOV_5, DEC_7), PayCycleStatus.PLANNED, List.of());

        final PayCycleResponse response = mapper.toResponse(view);

        assertThat(response.referenceMonth()).isNull();
        assertThat(response.anchorScheduleCode()).isNull();
        assertThat(response.kind()).isEqualTo(PayCycleKind.TRANSITION);
    }

    @Test
    void shouldCarryThePendenciesOfTheCycleInItsResponse() {
        final PayCycle cycle = buildCycle(NOV_5, DEC_7, NOV);
        final PayCyclePending pending = PayCyclePending.boundaryDiverges(cycle.code(), DEC, DEC_7, DEC_7.plusDays(1));
        final PayCycleView view = new PayCycleView(cycle, PayCycleStatus.PLANNED, List.of(pending));

        final PayCyclePendingResponse response = mapper.toResponse(view).pending().get(0);

        assertThat(response.type()).isEqualTo(PayCyclePending.Type.BOUNDARY_DIVERGES_FROM_ANCHOR);
        assertThat(response.nominalMonth()).isEqualTo(DEC);
        assertThat(response.cycleCode()).isEqualTo(cycle.code());
        assertThat(response.boundaryDate()).isEqualTo(DEC_7);
        assertThat(response.anchorDate()).isEqualTo(DEC_7.plusDays(1));
        assertThat(response.year()).isNull();
    }

    @Test
    void shouldMapACalendarPendencyWithTheYearToReview() {
        final PayCyclePendingResponse response = mapper.toResponse(PayCyclePending.calendarNotReviewed(YearMonth.of(2027, 1), 2027));

        assertThat(response.type()).isEqualTo(PayCyclePending.Type.CALENDAR_NOT_REVIEWED);
        assertThat(response.nominalMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(response.year()).isEqualTo(2027);
        assertThat(response.cycleCode()).isNull();
    }

    @Test
    void shouldMapAListOfCycleViewsKeepingTheOrder() {
        final List<PayCycleView> views = List.of(
                new PayCycleView(buildCycle(NOV_5, DEC_7, NOV), PayCycleStatus.OPEN, List.of()),
                new PayCycleView(buildCycle(DEC_7, JAN_5, DEC), PayCycleStatus.PLANNED, List.of()));

        assertThat(mapper.toResponseList(views)).extracting(PayCycleResponse::startDate).containsExactly(NOV_5, DEC_7);
    }

    @Test
    void shouldMapTheConfigurationViewWithItsPendencies() {
        final PayCycleConfigView view = new PayCycleConfigView(buildPayCycleConfig(NOV_5, 3L),
                List.of(PayCyclePending.noCurrentCycle()));

        final PayCycleConfigResponse response = mapper.toResponse(view);

        assertThat(response.mode()).isEqualTo(PayCycleMode.PAY_CYCLE);
        assertThat(response.anchorScheduleCode()).isEqualTo(SCHEDULE_CODE);
        assertThat(response.activationDate()).isEqualTo(NOV_5);
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.pending()).extracting(PayCyclePendingResponse::type)
                .containsExactly(PayCyclePending.Type.NO_CURRENT_CYCLE);
    }

    @Test
    void shouldMapTheConfigurationInCalendarMonthModeWithNullAnchorAndDate() {
        final PayCycleConfig config = buildCalendarMonthConfig();

        final PayCycleConfigResponse response = mapper.toResponse(config, List.of());

        assertThat(response.mode()).isEqualTo(PayCycleMode.CALENDAR_MONTH);
        assertThat(response.anchorScheduleCode()).isNull();
        assertThat(response.activationDate()).isNull();
        assertThat(response.pending()).isEmpty();
    }

    @Test
    void shouldMapThePreviewWithTheVersionToSendBack() {
        final PayCycleActivationPreview preview = new PayCycleActivationPreview(7L,
                List.of(new PayCycleView(buildCycle(NOV_5, DEC_7, NOV), PayCycleStatus.PLANNED, List.of())),
                List.of(PayCyclePending.calendarNotReviewed(YearMonth.of(2027, 1), 2027)));

        final PayCycleActivationPreviewResponse response = mapper.toResponse(preview);

        assertThat(response.expectedVersion()).isEqualTo(7L);
        assertThat(response.cycles()).hasSize(1);
        assertThat(response.pending()).hasSize(1);
    }

    @Test
    void shouldMapTheAnswerForNoCurrentCycleWithItsReasons() {
        final CurrentPayCycle current = new CurrentPayCycle(null, List.of(PayCyclePending.noCurrentCycle()));

        final PayCycleCurrentNotFoundResponse response = mapper.toNotFoundResponse(current);

        assertThat(response.message()).isEqualTo("Nenhum ciclo em andamento");
        assertThat(response.pending()).extracting(PayCyclePendingResponse::type)
                .containsExactly(PayCyclePending.Type.NO_CURRENT_CYCLE);
    }

    // ---- history of adjustments

    @Test
    void shouldMapAHistoryEntityToTheModelWithTheCodesOfTheCycles() {
        final PayCycleEntity previous = cycleEntity(UUID.randomUUID());
        final PayCycleEntity next = cycleEntity(UUID.randomUUID());
        final Instant createdDate = Instant.parse("2026-11-02T21:35:00Z");
        final PayCycleAdjustmentEntity entity = PayCycleAdjustmentEntity.builder()
                .id(3L)
                .code(UUID.randomUUID())
                .type(PayCycleAdjustmentType.MANUAL_BOUNDARY)
                .previousCycle(previous)
                .nextCycle(next)
                .oldBoundary(NOV_5)
                .newBoundary(NOV_10)
                .justification("Motivo")
                .expectedPreviousVersion(2L)
                .expectedNextVersion(5L)
                .createdDate(createdDate)
                .build();

        final PayCycleAdjustment model = mapper.toModel(entity);

        assertThat(model.code()).isEqualTo(entity.getCode());
        assertThat(model.type()).isEqualTo(PayCycleAdjustmentType.MANUAL_BOUNDARY);
        assertThat(model.previousCycleCode()).isEqualTo(previous.getCode());
        assertThat(model.nextCycleCode()).isEqualTo(next.getCode());
        assertThat(model.oldBoundary()).isEqualTo(NOV_5);
        assertThat(model.newBoundary()).isEqualTo(NOV_10);
        assertThat(model.justification()).isEqualTo("Motivo");
        assertThat(model.expectedPreviousVersion()).isEqualTo(2L);
        assertThat(model.expectedNextVersion()).isEqualTo(5L);
        assertThat(model.expectedConfigVersion()).isNull();
        assertThat(model.createdDate()).isEqualTo(createdDate);
    }

    @Test
    void shouldMapAHistoryEntityWithoutCyclesToNullCodes() {
        final PayCycleAdjustmentEntity entity = PayCycleAdjustmentEntity.builder()
                .code(UUID.randomUUID())
                .type(PayCycleAdjustmentType.DEACTIVATION)
                .justification("Motivo")
                .expectedConfigVersion(3L)
                .build();

        final PayCycleAdjustment model = mapper.toModel(entity);

        assertThat(model.previousCycleCode()).isNull();
        assertThat(model.nextCycleCode()).isNull();
        assertThat(model.expectedConfigVersion()).isEqualTo(3L);
    }

    @Test
    void shouldMapAnAdjustmentToANewEntityWithTheCyclesGivenAndNoCreationDate() {
        final PayCycle first = buildCycle(NOV_5.minusMonths(1), NOV_5, YearMonth.of(2026, 10));
        final PayCycle second = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleEntity previousEntity = cycleEntity(first.code());
        final PayCycleEntity nextEntity = cycleEntity(second.code());
        final PayCycleAdjustment adjustment = PayCycleAdjustment.manualBoundary(first, second, NOV_10, "Motivo", 2L, 5L);

        final PayCycleAdjustmentEntity entity = mapper.toEntity(adjustment, previousEntity, nextEntity);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCode()).isEqualTo(adjustment.code());
        assertThat(entity.getType()).isEqualTo(PayCycleAdjustmentType.MANUAL_BOUNDARY);
        assertThat(entity.getPreviousCycle()).isSameAs(previousEntity);
        assertThat(entity.getNextCycle()).isSameAs(nextEntity);
        assertThat(entity.getOldBoundary()).isEqualTo(NOV_5);
        assertThat(entity.getNewBoundary()).isEqualTo(NOV_10);
        assertThat(entity.getJustification()).isEqualTo("Motivo");
        assertThat(entity.getExpectedPreviousVersion()).isEqualTo(2L);
        assertThat(entity.getExpectedNextVersion()).isEqualTo(5L);
        assertThat(entity.getCreatedDate()).isNull();
    }

    @Test
    void shouldMapADeactivationToAnEntityThatMayHaveNoCycle() {
        final PayCycleAdjustment adjustment = PayCycleAdjustment.deactivation(null, null, null, "Motivo", 3L);

        final PayCycleAdjustmentEntity entity = mapper.toEntity(adjustment, null, null);

        assertThat(entity.getType()).isEqualTo(PayCycleAdjustmentType.DEACTIVATION);
        assertThat(entity.getPreviousCycle()).isNull();
        assertThat(entity.getNextCycle()).isNull();
        assertThat(entity.getOldBoundary()).isNull();
        assertThat(entity.getNewBoundary()).isNull();
        assertThat(entity.getExpectedConfigVersion()).isEqualTo(3L);
    }

    @Test
    void shouldMapAnAdjustmentToTheResponse() {
        final PayCycleAdjustment adjustment = PayCycleAdjustment.deactivation(
                UUID.randomUUID(), UUID.randomUUID(), DEC_7, "Motivo", 3L);

        final PayCycleAdjustmentResponse response = mapper.toResponse(adjustment);

        assertThat(response.code()).isEqualTo(adjustment.code());
        assertThat(response.type()).isEqualTo(PayCycleAdjustmentType.DEACTIVATION);
        assertThat(response.previousCycleCode()).isEqualTo(adjustment.previousCycleCode());
        assertThat(response.nextCycleCode()).isEqualTo(adjustment.nextCycleCode());
        assertThat(response.oldBoundary()).isEqualTo(DEC_7);
        assertThat(response.newBoundary()).isNull();
        assertThat(response.expectedConfigVersion()).isEqualTo(3L);
        assertThat(mapper.toAdjustmentResponseList(List.of(adjustment, adjustment))).hasSize(2);
    }

    // ---- end adjustment

    @Test
    void shouldMapTheMovementThatChangesCycleWithAPositiveAmountForADebit() {
        final FinancialMovement debit = FinancialMovementFactory.buildModel(
                LocalDate.of(2026, 11, 7), OperationType.DEBIT, "120.00", FinancialMovementStatus.PENDING);

        final PayCycleMovementImpactResponse response = mapper.toResponse(debit);

        assertThat(response.code()).isEqualTo(debit.code());
        assertThat(response.description()).isEqualTo(debit.description());
        assertThat(response.operationType()).isEqualTo(OperationType.DEBIT);
        assertThat(response.amount()).isEqualByComparingTo("120.00");
        assertThat(response.status()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(response.competenceDate()).isEqualTo(debit.competenceDate());
    }

    @Test
    void shouldKeepTheAmountOfACreditAsItIs() {
        final FinancialMovement credit = FinancialMovementFactory.buildModel(
                LocalDate.of(2026, 11, 7), OperationType.CREDIT, "500.00", FinancialMovementStatus.PENDING);

        assertThat(mapper.toResponse(credit).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldMapTheIncomeOccurrenceThatChangesCycle() {
        final UUID movementCode = UUID.randomUUID();
        final IncomeOccurrenceImpact impact = new IncomeOccurrenceImpact(SCHEDULE_CODE, "Salário",
                new IncomeOccurrenceView(NOV, IncomeOccurrenceView.State.MATERIALIZED, LocalDate.of(2026, 11, 6),
                        new BigDecimal("5000.00"), movementCode, null));

        final PayCycleIncomeImpactResponse response = mapper.toResponse(impact);

        assertThat(response.scheduleCode()).isEqualTo(SCHEDULE_CODE);
        assertThat(response.scheduleDescription()).isEqualTo("Salário");
        assertThat(response.nominalMonth()).isEqualTo(NOV);
        assertThat(response.state()).isEqualTo(IncomeOccurrenceView.State.MATERIALIZED);
        assertThat(response.plannedDate()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.movementCode()).isEqualTo(movementCode);
    }

    @Test
    void shouldMapThePreviewWithTheVersionsOfBothCyclesToSendBack() {
        final PayCycle first = new PayCycle(null, NOV_5.minusMonths(1), NOV_5, PayCycleKind.REGULAR, SCHEDULE_CODE,
                YearMonth.of(2026, 10), PayCycleLifecycle.ACTIVE, false, false, 4L);
        final PayCycle second = new PayCycle(null, NOV_5, DEC_7, PayCycleKind.REGULAR, SCHEDULE_CODE, NOV,
                PayCycleLifecycle.ACTIVE, false, false, 9L);
        final PayCycleAdjustmentPlanner.EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(first, second, NOV_10);
        final PayCycleEndAdjustmentPreview preview = new PayCycleEndAdjustmentPreview(NOV_5, NOV_10,
                List.of(new PayCycleView(first, PayCycleStatus.OPEN, List.of()),
                        new PayCycleView(second, PayCycleStatus.PLANNED, List.of())),
                List.of(new PayCycleView(adjustment.previous(), PayCycleStatus.OPEN, List.of()),
                        new PayCycleView(adjustment.next(), PayCycleStatus.PLANNED, List.of())),
                second.code(), first.code(), List.of(), List.of());

        final PayCycleEndAdjustmentPreviewResponse response = mapper.toResponse(preview);

        assertThat(response.oldEndDate()).isEqualTo(NOV_5);
        assertThat(response.newEndDate()).isEqualTo(NOV_10);
        assertThat(response.expectedVersion()).isEqualTo(4L);
        assertThat(response.expectedNextVersion()).isEqualTo(9L);
        assertThat(response.cyclesBefore()).extracting(PayCycleResponse::endDateExclusive).containsExactly(NOV_5, DEC_7);
        assertThat(response.cyclesAfter()).extracting(PayCycleResponse::endDateExclusive).containsExactly(NOV_10, DEC_7);
        assertThat(response.cyclesAfter()).extracting(PayCycleResponse::startDate).containsExactly(first.startDate(), NOV_10);
        assertThat(response.cyclesAfter().get(0).endAdjusted()).isTrue();
        assertThat(response.cyclesAfter().get(1).startAdjusted()).isTrue();
        assertThat(response.leavingCycleCode()).isEqualTo(second.code());
        assertThat(response.receivingCycleCode()).isEqualTo(first.code());
        assertThat(response.movements()).isEmpty();
        assertThat(response.incomeOccurrences()).isEmpty();
    }

    @Test
    void shouldMapTheAnswerOfAnAdjustmentWithTheEntryAndBothCycles() {
        final PayCycle first = buildCycle(NOV_5.minusMonths(1), NOV_5, YearMonth.of(2026, 10));
        final PayCycle second = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleAdjustmentPlanner.EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(first, second, NOV_10);
        final PayCycleAdjustment entry = PayCycleAdjustment.manualBoundary(first, second, NOV_10, "Motivo", 0L, 0L);

        final PayCycleEndAdjustmentResponse response = mapper.toResponse(new PayCycleEndAdjustmentResult(entry,
                new PayCycleView(adjustment.previous(), PayCycleStatus.OPEN, List.of()),
                new PayCycleView(adjustment.next(), PayCycleStatus.PLANNED, List.of())));

        assertThat(response.adjustment().code()).isEqualTo(entry.code());
        assertThat(response.cycle().code()).isEqualTo(first.code());
        assertThat(response.cycle().endDateExclusive()).isEqualTo(NOV_10);
        assertThat(response.nextCycle().code()).isEqualTo(second.code());
        assertThat(response.nextCycle().startDate()).isEqualTo(NOV_10);
    }

    private PayCycleEntity cycleEntity(final UUID code) {
        return PayCycleEntity.builder()
                .id((long) (code.hashCode() & 0xffff))
                .code(code)
                .startDate(NOV_5.minusMonths(1))
                .endDate(NOV_5)
                .kind(PayCycleKind.REGULAR)
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .version(0L)
                .build();
    }
}
