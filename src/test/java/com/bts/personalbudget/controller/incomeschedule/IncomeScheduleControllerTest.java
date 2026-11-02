package com.bts.personalbudget.controller.incomeschedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import com.bts.personalbudget.core.domain.service.income.MaterializationResult;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class IncomeScheduleControllerTest {

    private static final UUID CODE = IncomeScheduleFactory.SCHEDULE_CODE;
    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @InjectMocks
    private IncomeScheduleController controller;

    @Mock
    private IncomeScheduleService incomeScheduleService;

    @Spy
    private IncomeScheduleMapper incomeScheduleMapper = IncomeScheduleMapper.INSTANCE;

    @Test
    void shouldReturnCreatedWithTheScheduleInTheBody() {
        final IncomeScheduleRequest request = new IncomeScheduleRequest("Salário", new BigDecimal("5000.00"), null,
                DateRuleType.FIXED_DAY, 5, BusinessWeek.MONDAY_TO_FRIDAY, NonBusinessDayPolicy.POSTPONE,
                YearMonth.of(2026, 12), null, true, null);
        when(incomeScheduleService.create(any(IncomeSchedule.class))).thenAnswer(call -> call.getArgument(0));

        final ResponseEntity<IncomeScheduleResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).isEqualTo("Salário");
        assertThat(response.getBody().ruleType()).isEqualTo(DateRuleType.FIXED_DAY);
        assertThat(response.getBody().startMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(response.getBody().anchor()).isTrue();
        assertThat(response.getBody().status()).isEqualTo(IncomeScheduleStatus.ACTIVE);
    }

    @Test
    void shouldAskForTheDefaultStatusesWhenNoneIsSent() {
        when(incomeScheduleService.find(null)).thenReturn(List.of(IncomeScheduleFactory.buildSchedule(JUNE)));

        final ResponseEntity<List<IncomeScheduleResponse>> response = controller.find(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(incomeScheduleService).find(null);
    }

    @Test
    void shouldPassTheRequestedStatusesToTheService() {
        final List<IncomeScheduleStatus> statuses = List.of(IncomeScheduleStatus.ACTIVE, IncomeScheduleStatus.INACTIVE);
        when(incomeScheduleService.find(statuses)).thenReturn(List.of());

        final ResponseEntity<List<IncomeScheduleResponse>> response = controller.find(statuses);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void shouldReturnTheScheduleOfTheCode() {
        when(incomeScheduleService.findByCode(CODE)).thenReturn(IncomeScheduleFactory.buildSchedule(JUNE));

        final ResponseEntity<IncomeScheduleResponse> response = controller.findByCode(CODE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(CODE);
    }

    @Test
    void shouldTreatAnOmittedRemoveEndMonthAsFalseOnUpdate() {
        when(incomeScheduleService.update(any(UUID.class), any(IncomeScheduleUpdate.class)))
                .thenReturn(IncomeScheduleFactory.buildSchedule(JUNE));

        final ResponseEntity<IncomeScheduleResponse> response = controller.update(CODE,
                new IncomeScheduleUpdateRequest(null, new BigDecimal("5200.00"), null, null, null, null, null, null,
                        null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final ArgumentCaptor<IncomeScheduleUpdate> captor = ArgumentCaptor.forClass(IncomeScheduleUpdate.class);
        verify(incomeScheduleService).update(eq(CODE), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("5200.00");
        assertThat(captor.getValue().removeEndMonth()).isFalse();
    }

    @Test
    void shouldReturnOkWithTheInactivatedSchedule() {
        when(incomeScheduleService.inactivate(CODE))
                .thenReturn(IncomeScheduleFactory.buildInactive(IncomeScheduleFactory.buildSchedule(JUNE)));

        final ResponseEntity<IncomeScheduleResponse> response = controller.inactivate(CODE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(IncomeScheduleStatus.INACTIVE);
    }

    @Test
    void shouldReturnOkWithTheActivatedSchedule() {
        when(incomeScheduleService.activate(CODE)).thenReturn(IncomeScheduleFactory.buildSchedule(JUNE));

        final ResponseEntity<IncomeScheduleResponse> response = controller.activate(CODE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(IncomeScheduleStatus.ACTIVE);
    }

    @Test
    void shouldParseTheMonthsOfTheOccurrenceListing() {
        when(incomeScheduleService.findOccurrences(CODE, JUNE, YearMonth.of(2026, 8))).thenReturn(List.of(
                IncomeOccurrenceView.projected(JUNE, LocalDate.of(2026, 6, 5), IncomeScheduleFactory.AMOUNT)));

        final ResponseEntity<List<IncomeOccurrenceResponse>> response =
                controller.findOccurrences(CODE, "2026-06", "2026-08");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).state()).isEqualTo(State.PROJECTED);
    }

    @Test
    void shouldLeaveTheDefaultsToTheServiceWhenTheMonthsAreOmitted() {
        when(incomeScheduleService.findOccurrences(CODE, null, null)).thenReturn(List.of());

        final ResponseEntity<List<IncomeOccurrenceResponse>> response = controller.findOccurrences(CODE, null, null);

        assertThat(response.getBody()).isEmpty();
        verify(incomeScheduleService).findOccurrences(CODE, null, null);
    }

    @Test
    void shouldRejectAMalformedMonthOnTheListingWithoutCallingTheService() {
        assertThatThrownBy(() -> controller.findOccurrences(CODE, "2026-13", null))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("from"));
        assertThatThrownBy(() -> controller.findOccurrences(CODE, null, "junho"))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("to"));
        assertThatThrownBy(() -> controller.findOccurrences(CODE, "2026-06-01", null))
                .isInstanceOf(InvalidFieldsException.class);
        verifyNoInteractions(incomeScheduleService);
    }

    @Test
    void shouldAnswerCreatedWhenTheMaterializationCreatesTheOccurrence() {
        when(incomeScheduleService.materialize(CODE, JUNE)).thenReturn(new MaterializationResult(
                IncomeScheduleFactory.buildOccurrence(JUNE, LocalDate.of(2026, 6, 5)), true));

        final ResponseEntity<IncomeOccurrenceResponse> response = controller.materialize(CODE, "2026-06");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().state()).isEqualTo(State.MATERIALIZED);
        assertThat(response.getBody().movementCode()).isEqualTo(IncomeScheduleFactory.MOVEMENT_CODE);
        assertThat(response.getBody().plannedDate()).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    void shouldAnswerOkWhenTheOccurrenceAlreadyExisted() {
        when(incomeScheduleService.materialize(CODE, JUNE)).thenReturn(new MaterializationResult(
                IncomeScheduleFactory.buildOccurrence(JUNE, LocalDate.of(2026, 6, 5)), false));

        final ResponseEntity<IncomeOccurrenceResponse> response = controller.materialize(CODE, "2026-06");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().state()).isEqualTo(State.MATERIALIZED);
    }

    @Test
    void shouldRejectAMalformedMonthOnMaterializeWithoutCallingTheService() {
        assertThatThrownBy(() -> controller.materialize(CODE, "2026/06"))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("month"));
        verifyNoInteractions(incomeScheduleService);
    }
}
