package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.exception.InvalidFieldsException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialMovementControllerTest {

    @InjectMocks
    private FinancialMovementController controller;

    @Mock
    private FinancialMovementService service;

    @Spy
    private FinancialMovementMapper mapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldReturnCreatedMovementInBody() {
        final LocalDateTime now = LocalDateTime.now();
        final FinancialMovementRequest request = new FinancialMovementRequest(
                OperationType.DEBIT, null, "Padaria", new BigDecimal("12.50"), new BigDecimal("12.50"),
                now, now, now, FinancialMovementStatus.PAID_OUT, null, null);
        final FinancialMovement created = FinancialMovementFactory.buildModel();
        when(service.create(any(FinancialMovement.class))).thenReturn(created);

        final ResponseEntity<FinancialMovementResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(created.code());
    }

    @Test
    void shouldPassManualCompetenceOfCreateRequestToService() {
        final LocalDateTime now = LocalDateTime.now();
        final LocalDate competence = LocalDate.of(2026, 10, 1);
        final FinancialMovementRequest request = new FinancialMovementRequest(
                OperationType.DEBIT, null, "Padaria", new BigDecimal("12.50"), null,
                now, now, null, FinancialMovementStatus.PENDING, null, competence);
        when(service.create(any(FinancialMovement.class))).thenReturn(FinancialMovementFactory.buildModel());

        controller.create(request);

        final ArgumentCaptor<FinancialMovement> captor = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().competenceDate()).isEqualTo(competence);
    }

    @Test
    void shouldPassCompetenceCommandsOfUpdateRequestToService() {
        final UUID code = UUID.randomUUID();
        final FinancialMovementUpdateRequest request = new FinancialMovementUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, true);
        when(service.update(any(FinancialMovementUpdate.class))).thenReturn(FinancialMovementFactory.buildModel());

        controller.update(code, request);

        final ArgumentCaptor<FinancialMovementUpdate> captor = ArgumentCaptor.forClass(FinancialMovementUpdate.class);
        verify(service).update(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo(code);
        assertThat(captor.getValue().competenceDate()).isNull();
        assertThat(captor.getValue().restoreAutomaticCompetence()).isTrue();
    }

    @Test
    void shouldKeepCompetenceUntouchedWhenUpdateRequestOmitsIt() {
        final FinancialMovementUpdateRequest request = new FinancialMovementUpdateRequest(
                null, "Novo nome", null, null, null, null, null, null, null, null, null);
        when(service.update(any(FinancialMovementUpdate.class))).thenReturn(FinancialMovementFactory.buildModel());

        controller.update(UUID.randomUUID(), request);

        final ArgumentCaptor<FinancialMovementUpdate> captor = ArgumentCaptor.forClass(FinancialMovementUpdate.class);
        verify(service).update(captor.capture());
        assertThat(captor.getValue().competenceDate()).isNull();
        assertThat(captor.getValue().restoreAutomaticCompetence()).isFalse();
    }

    @Test
    void shouldRejectUpdateRequestWithManualCompetenceAndRestoreTogether() {
        final FinancialMovementUpdateRequest request = new FinancialMovementUpdateRequest(
                null, null, null, null, null, null, null, null, null,
                LocalDate.of(2026, 10, 1), true);

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> controller.update(UUID.randomUUID(), request));

        assertThat(exception.getValidations()).containsKey("competenceDate");
        verifyNoInteractions(service);
    }
}
