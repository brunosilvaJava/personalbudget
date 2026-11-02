package com.bts.personalbudget.mapper;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.CONFIG_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildTransition;
import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleConfigResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCyclePendingResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleActivationPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleMapperTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final YearMonth DEC = YearMonth.of(2026, 12);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
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
}
