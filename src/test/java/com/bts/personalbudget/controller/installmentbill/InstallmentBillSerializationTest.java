package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentBillSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeInstallmentCountAndNextInstallmentDateInSnakeCase() {
        final InstallmentBillResponse response = new InstallmentBillResponse(
                null, OperationType.DEBIT, "Notebook", new BigDecimal("100.00"),
                InstallmentBillStatus.PENDING, LocalDate.of(2026, 1, 1), 10, 3,
                LocalDate.of(2026, 5, 1), null);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).contains("installment_count", "next_installment_date")
                .doesNotContain("installmentCount", "nextInstallmentDate");
        assertThat(json.get("installment_count").asInt()).isEqualTo(3);
        assertThat(json.get("next_installment_date").asText()).isEqualTo("2026-05-01");
    }

    private List<String> fieldNames(final JsonNode json) {
        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
