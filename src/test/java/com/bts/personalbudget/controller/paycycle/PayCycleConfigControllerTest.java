package com.bts.personalbudget.controller.paycycle;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleActivationPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.mapper.PayCycleMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PayCycleConfigControllerTest {

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);

    @InjectMocks
    private PayCycleConfigController controller;

    @Mock
    private PayCycleService payCycleService;

    @Spy
    private PayCycleMapper payCycleMapper = PayCycleMapper.INSTANCE;

    @Test
    void shouldReturnOkWithTheConfiguration() {
        when(payCycleService.findConfig()).thenReturn(new PayCycleConfigView(buildCalendarMonthConfig(), List.of()));

        final ResponseEntity<PayCycleConfigResponse> response = controller.find();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().mode()).isEqualTo(PayCycleMode.CALENDAR_MONTH);
        assertThat(response.getBody().version()).isZero();
        assertThat(response.getBody().pending()).isEmpty();
    }

    @Test
    void shouldReturnOkWithThePendenciesOfTheConfiguration() {
        final PayCyclePending pending = PayCyclePending.calendarNotReviewed(YearMonth.of(2027, 1), 2027);
        when(payCycleService.findConfig())
                .thenReturn(new PayCycleConfigView(buildPayCycleConfig(NOV_5, 2L), List.of(pending)));

        final ResponseEntity<PayCycleConfigResponse> response = controller.find();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().anchorScheduleCode()).isEqualTo(SCHEDULE_CODE);
        assertThat(response.getBody().pending()).hasSize(1);
        assertThat(response.getBody().pending().get(0).year()).isEqualTo(2027);
    }

    @Test
    void shouldAnswerOkWithThePreviewWithoutCreatingAnything() {
        final PayCycleActivationPreview preview = new PayCycleActivationPreview(0L,
                List.of(new PayCycleView(buildCycle(NOV_5, DEC_7, YearMonth.of(2026, 11)), PayCycleStatus.PLANNED, List.of())),
                List.of());
        when(payCycleService.previewActivation(NOV_5)).thenReturn(preview);

        final ResponseEntity<PayCycleActivationPreviewResponse> response =
                controller.previewActivation(new PayCycleActivationPreviewRequest(NOV_5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().expectedVersion()).isZero();
        assertThat(response.getBody().cycles()).hasSize(1);
        assertThat(response.getBody().cycles().get(0).label()).isEqualTo("Nov/Dez");
    }

    @Test
    void shouldAnswerOkWithTheConfigurationAfterTheActivation() {
        when(payCycleService.activate(NOV_5, 0L))
                .thenReturn(new PayCycleConfigView(buildPayCycleConfig(NOV_5, 1L), List.of()));

        final ResponseEntity<PayCycleConfigResponse> response =
                controller.activate(new PayCycleActivationRequest(NOV_5, 0L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().mode()).isEqualTo(PayCycleMode.PAY_CYCLE);
        assertThat(response.getBody().activationDate()).isEqualTo(NOV_5);
        assertThat(response.getBody().version()).isEqualTo(1L);
        verify(payCycleService).activate(NOV_5, 0L);
    }

    @Test
    void shouldAnswerOkWithTheConfigurationAfterTheDeactivation() {
        when(payCycleService.deactivate(1L))
                .thenReturn(new PayCycleConfigView(buildCalendarMonthConfig(), List.of()));

        final ResponseEntity<PayCycleConfigResponse> response =
                controller.deactivate(new PayCycleDeactivationRequest(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().mode()).isEqualTo(PayCycleMode.CALENDAR_MONTH);
        assertThat(response.getBody().anchorScheduleCode()).isNull();
        verify(payCycleService).deactivate(1L);
    }
}
