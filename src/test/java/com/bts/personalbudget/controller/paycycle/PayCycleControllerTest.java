package com.bts.personalbudget.controller.paycycle;

import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.mapper.PayCycleMapper;
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
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
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

    private PayCycleView view() {
        final PayCycle cycle = buildCycle(NOV_5, DEC_7, NOV);
        return new PayCycleView(cycle, PayCycleStatus.OPEN, List.of());
    }
}
