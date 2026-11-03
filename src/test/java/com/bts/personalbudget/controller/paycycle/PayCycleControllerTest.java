package com.bts.personalbudget.controller.paycycle;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustment;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustmentPlanner;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentResult;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.mapper.PayCycleMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PayCycleControllerTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);

    @InjectMocks
    private PayCycleController controller;

    @Mock
    private PayCycleService payCycleService;

    @Spy
    private PayCycleMapper payCycleMapper = PayCycleMapper.INSTANCE;

    @Test
    void shouldListTheCyclesWithoutFiltersAndWithoutTheCancelledOnes() {
        when(payCycleService.findCycles(null, null, null, false)).thenReturn(List.of(view()));

        final ResponseEntity<List<PayCycleResponse>> response = controller.find(null, null, null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).startDate()).isEqualTo(NOV_5);
        assertThat(response.getBody().get(0).label()).isEqualTo("Nov/Dez");
    }

    @Test
    void shouldPassTheFiltersToTheService() {
        final LocalDate from = LocalDate.of(2026, 11, 1);
        final LocalDate to = LocalDate.of(2026, 12, 31);
        when(payCycleService.findCycles(null, from, to, true)).thenReturn(List.of());

        final ResponseEntity<List<PayCycleResponse>> response = controller.find(null, from, to, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(payCycleService).findCycles(null, from, to, true);
    }

    @Test
    void shouldPassTheDateToTheService() {
        when(payCycleService.findCycles(DEC_7, null, null, false)).thenReturn(List.of(view()));

        controller.find(DEC_7, null, null, false);

        verify(payCycleService).findCycles(DEC_7, null, null, false);
    }

    @Test
    void shouldReturnOkWithTheCurrentCycle() {
        final PayCycleView view = view();
        when(payCycleService.findCurrent()).thenReturn(new CurrentPayCycle(view, List.of()));

        final ResponseEntity<?> response = controller.findCurrent();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PayCycleResponse.class);
        assertThat(((PayCycleResponse) response.getBody()).code()).isEqualTo(view.cycle().code());
        assertThat(((PayCycleResponse) response.getBody()).status()).isEqualTo(PayCycleStatus.OPEN);
    }

    @Test
    void shouldReturnNotFoundWithTheReasonsWhenThereIsNoCurrentCycle() {
        when(payCycleService.findCurrent())
                .thenReturn(new CurrentPayCycle(null, List.of(PayCyclePending.noCurrentCycle())));

        final ResponseEntity<?> response = controller.findCurrent();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(PayCycleCurrentNotFoundResponse.class);
        final PayCycleCurrentNotFoundResponse body = (PayCycleCurrentNotFoundResponse) response.getBody();
        assertThat(body.message()).isEqualTo("Nenhum ciclo em andamento");
        assertThat(body.pending()).extracting(PayCyclePendingResponse::type)
                .containsExactly(PayCyclePending.Type.NO_CURRENT_CYCLE);
    }

    @Test
    void shouldReturnNotFoundWithNoReasonsWhenThePayCyclesAreOff() {
        when(payCycleService.findCurrent()).thenReturn(new CurrentPayCycle(null, List.of()));

        final ResponseEntity<?> response = controller.findCurrent();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((PayCycleCurrentNotFoundResponse) response.getBody()).pending()).isEmpty();
    }

    @Test
    void shouldReturnTheCycleOfTheCode() {
        final PayCycleView view = view();
        final UUID code = view.cycle().code();
        when(payCycleService.findByCode(code)).thenReturn(view);

        final ResponseEntity<PayCycleResponse> response = controller.findByCode(code);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().endDateExclusive()).isEqualTo(DEC_7);
    }

    // ---- end adjustment

    @Test
    void shouldAnswerOkWithThePreviewOfTheEndAdjustment() {
        final PayCycle first = buildCycle(OCT_5, NOV_5, YearMonth.of(2026, 10));
        final PayCycle second = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleAdjustmentPlanner.EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(first, second, NOV_10);
        final FinancialMovement movement = FinancialMovementFactory.buildModel(
                LocalDate.of(2026, 11, 7), OperationType.DEBIT, "120.00", FinancialMovementStatus.PENDING);
        final IncomeOccurrenceImpact income = new IncomeOccurrenceImpact(SCHEDULE_CODE, "Salário",
                new IncomeOccurrenceView(NOV, IncomeOccurrenceView.State.PROJECTED, LocalDate.of(2026, 11, 7),
                        new BigDecimal("1000.00"), null, null));
        when(payCycleService.previewEndAdjustment(first.code(), NOV_10)).thenReturn(new PayCycleEndAdjustmentPreview(
                NOV_5, NOV_10,
                List.of(new PayCycleView(first, PayCycleStatus.OPEN, List.of()),
                        new PayCycleView(second, PayCycleStatus.PLANNED, List.of())),
                List.of(new PayCycleView(adjustment.previous(), PayCycleStatus.OPEN, List.of()),
                        new PayCycleView(adjustment.next(), PayCycleStatus.PLANNED, List.of())),
                second.code(), first.code(), List.of(movement), List.of(income)));

        final ResponseEntity<PayCycleEndAdjustmentPreviewResponse> response = controller.previewEndAdjustment(
                first.code(), new PayCycleEndAdjustmentPreviewRequest(NOV_10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final PayCycleEndAdjustmentPreviewResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.oldEndDate()).isEqualTo(NOV_5);
        assertThat(body.newEndDate()).isEqualTo(NOV_10);
        assertThat(body.expectedVersion()).isZero();
        assertThat(body.expectedNextVersion()).isZero();
        assertThat(body.cyclesBefore()).extracting(PayCycleResponse::endDateExclusive).containsExactly(NOV_5, DEC_7);
        assertThat(body.cyclesAfter()).extracting(PayCycleResponse::endDateExclusive).containsExactly(NOV_10, DEC_7);
        assertThat(body.leavingCycleCode()).isEqualTo(second.code());
        assertThat(body.receivingCycleCode()).isEqualTo(first.code());
        assertThat(body.movements()).hasSize(1);
        assertThat(body.movements().get(0).description()).isEqualTo(movement.description());
        assertThat(body.movements().get(0).amount()).isPositive();
        assertThat(body.incomeOccurrences()).hasSize(1);
        assertThat(body.incomeOccurrences().get(0).scheduleDescription()).isEqualTo("Salário");
        assertThat(body.incomeOccurrences().get(0).plannedDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void shouldAnswerOkWithTheCyclesAndTheHistoryEntryAfterTheEndAdjustment() {
        final PayCycle first = buildCycle(OCT_5, NOV_5, YearMonth.of(2026, 10));
        final PayCycle second = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleAdjustmentPlanner.EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(first, second, NOV_10);
        final PayCycleAdjustment entry = PayCycleAdjustment.manualBoundary(first, second, NOV_10, "Motivo", 0L, 0L);
        when(payCycleService.adjustEnd(first.code(), NOV_10, "Motivo", 0L, 0L)).thenReturn(new PayCycleEndAdjustmentResult(
                entry,
                new PayCycleView(adjustment.previous(), PayCycleStatus.OPEN, List.of()),
                new PayCycleView(adjustment.next(), PayCycleStatus.PLANNED, List.of())));

        final ResponseEntity<PayCycleEndAdjustmentResponse> response = controller.adjustEnd(
                first.code(), new PayCycleEndAdjustmentRequest(NOV_10, "Motivo", 0L, 0L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final PayCycleEndAdjustmentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.adjustment().type()).isEqualTo(PayCycleAdjustmentType.MANUAL_BOUNDARY);
        assertThat(body.adjustment().oldBoundary()).isEqualTo(NOV_5);
        assertThat(body.adjustment().newBoundary()).isEqualTo(NOV_10);
        assertThat(body.adjustment().justification()).isEqualTo("Motivo");
        assertThat(body.cycle().code()).isEqualTo(first.code());
        assertThat(body.cycle().endDateExclusive()).isEqualTo(NOV_10);
        assertThat(body.cycle().endAdjusted()).isTrue();
        assertThat(body.nextCycle().code()).isEqualTo(second.code());
        assertThat(body.nextCycle().startDate()).isEqualTo(NOV_10);
        assertThat(body.nextCycle().startAdjusted()).isTrue();
        verify(payCycleService).adjustEnd(first.code(), NOV_10, "Motivo", 0L, 0L);
    }

    @Test
    void shouldAnswerOkWithTheHistoryOfTheCycle() {
        final PayCycle first = buildCycle(OCT_5, NOV_5, YearMonth.of(2026, 10));
        final PayCycle second = buildCycle(NOV_5, DEC_7, NOV);
        final PayCycleAdjustment entry = PayCycleAdjustment.manualBoundary(first, second, NOV_10, "Motivo", 0L, 0L);
        when(payCycleService.findAdjustments(second.code())).thenReturn(List.of(entry));

        final ResponseEntity<List<PayCycleAdjustmentResponse>> response = controller.findAdjustments(second.code());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).code()).isEqualTo(entry.code());
        assertThat(response.getBody().get(0).previousCycleCode()).isEqualTo(first.code());
        assertThat(response.getBody().get(0).nextCycleCode()).isEqualTo(second.code());
        assertThat(response.getBody().get(0).expectedPreviousVersion()).isZero();
        assertThat(response.getBody().get(0).expectedConfigVersion()).isNull();
    }

    @Test
    void shouldAnswerOkWithAnEmptyHistory() {
        final UUID code = UUID.randomUUID();
        when(payCycleService.findAdjustments(code)).thenReturn(List.of());

        final ResponseEntity<List<PayCycleAdjustmentResponse>> response = controller.findAdjustments(code);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void shouldAnswerTheDeactivationInTheHistoryWithTheConfigurationVersion() {
        final UUID started = UUID.randomUUID();
        final PayCycleAdjustment entry = PayCycleAdjustment.deactivation(started, null, null, "Motivo", 3L);
        when(payCycleService.findAdjustments(started)).thenReturn(List.of(entry));

        final ResponseEntity<List<PayCycleAdjustmentResponse>> response = controller.findAdjustments(started);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).type()).isEqualTo(PayCycleAdjustmentType.DEACTIVATION);
        assertThat(response.getBody().get(0).newBoundary()).isNull();
        assertThat(response.getBody().get(0).expectedConfigVersion()).isEqualTo(3L);
    }

    private PayCycleView view() {
        final PayCycle cycle = buildCycle(NOV_5, DEC_7, NOV);
        return new PayCycleView(cycle, PayCycleStatus.OPEN, List.of());
    }
}
