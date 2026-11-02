package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                now, now, now, FinancialMovementStatus.PAID_OUT, null);
        final FinancialMovement created = FinancialMovementFactory.buildModel();
        when(service.create(any(FinancialMovement.class))).thenReturn(created);

        final ResponseEntity<FinancialMovementResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(created.code());
    }
}
