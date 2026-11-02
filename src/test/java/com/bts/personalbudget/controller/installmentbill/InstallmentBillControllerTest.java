package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentBillControllerTest {

    @InjectMocks
    private InstallmentBillController controller;

    @Mock
    private InstallmentBillService installmentBillService;

    @Mock
    private InstallmentBillMapper mapper;

    @Test
    void shouldReturnNoContentWhenInstallmentBillIsDeleted() {
        final UUID code = UUID.randomUUID();

        final ResponseEntity<Void> response = controller.delete(code);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(installmentBillService).delete(code);
    }

    @Test
    void shouldReturnCreatedInstallmentBillInBody() {
        final InstallmentBillRequest request = new InstallmentBillRequest(
                OperationType.DEBIT, "Geladeira", new BigDecimal("300.00"), LocalDate.now(), 10);
        final InstallmentBill model = InstallmentBillFactory.buildModel();
        when(mapper.toModel(request)).thenReturn(model);
        when(installmentBillService.create(model)).thenReturn(model);

        final ResponseEntity<InstallmentBillResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(mapper).toResponse(model);
    }
}
