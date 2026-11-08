package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMovementCompetenceSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeCompetenceFieldsInSnakeCase() {
        final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
        final FinancialMovementResponse response = new FinancialMovementResponse(
                UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"), null,
                now, now, FinancialMovementStatus.PENDING, null, null, null,
                LocalDate.of(2026, 11, 1), true);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("competence_date").asText()).isEqualTo("2026-11-01");
        assertThat(json.get("competence_overridden").asBoolean()).isTrue();
        assertThat(json.has("competenceDate")).isFalse();
        assertThat(json.has("competenceOverridden")).isFalse();
        assertThat(json.has("pay_date")).isFalse();
    }

    @Test
    void shouldDeserializeCompetenceFieldsOfCreateRequestFromSnakeCase() throws Exception {
        final String body = """
                {"operation_type":"DEBIT","description":"Padaria","amount":12.50,
                 "movement_date":"2026-11-05T10:00:00","due_date":"2026-11-05T10:00:00",
                 "competence_date":"2026-10-01"}""";

        final FinancialMovementRequest request = objectMapper.readValue(body, FinancialMovementRequest.class);

        assertThat(request.competenceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void shouldDeserializeCompetenceCommandsOfUpdateRequestFromSnakeCase() throws Exception {
        final FinancialMovementUpdateRequest manual = objectMapper.readValue(
                "{\"competence_date\":\"2026-10-01\"}", FinancialMovementUpdateRequest.class);
        final FinancialMovementUpdateRequest restore = objectMapper.readValue(
                "{\"restore_automatic_competence\":true}", FinancialMovementUpdateRequest.class);
        final FinancialMovementUpdateRequest omitted = objectMapper.readValue(
                "{\"description\":\"Novo nome\"}", FinancialMovementUpdateRequest.class);

        assertThat(manual.competenceDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(restore.restoreAutomaticCompetence()).isTrue();
        assertThat(omitted.competenceDate()).isNull();
        assertThat(omitted.restoreAutomaticCompetence()).isNull();
    }

    @Test
    void shouldRejectLegacyPaymentFieldsInMovementWriteRequests() throws Exception {
        final FinancialMovementRequest request = objectMapper.readValue("""
                {"operation_type":"DEBIT","description":"Padaria","amount":12.50,
                 "movement_date":"2026-11-05T10:00:00","due_date":"2026-11-05T10:00:00",
                 "amount_paid":5.00,"status":"PAID_OUT"}""", FinancialMovementRequest.class);

        assertThat(request.legacyAmountPaid()).isEqualByComparingTo("5.00");
        assertThat(request.legacyStatus()).isEqualTo(FinancialMovementStatus.PAID_OUT);
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request))
                    .extracting(violation -> violation.getMessage())
                    .anyMatch(message -> message.contains("settlements"));
            final FinancialMovementUpdateRequest update = objectMapper.readValue(
                    "{\"pay_date\":\"2026-11-05T10:00:00\"}", FinancialMovementUpdateRequest.class);
            assertThat(validatorFactory.getValidator().validate(update))
                    .extracting(violation -> violation.getMessage())
                    .anyMatch(message -> message.contains("settlements"));
        }
    }
}
