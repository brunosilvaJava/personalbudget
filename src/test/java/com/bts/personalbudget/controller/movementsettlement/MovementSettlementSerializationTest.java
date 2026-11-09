package com.bts.personalbudget.controller.movementsettlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovementSettlementSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeSettlementResponseUsingSnakeCaseAndRetainReversalMetadata() {
        final UUID accountCode = UUID.randomUUID();
        final MovementSettlementResponse response = new MovementSettlementResponse(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.50"),
                LocalDate.of(2026, 11, 8), LocalDateTime.of(2026, 11, 9, 10, 0), 1L, accountCode);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("movement_code")).isNotNull();
        assertThat(json.get("effective_date").asText()).isEqualTo("2026-11-08");
        assertThat(json.get("reversed_at").asText()).isEqualTo("2026-11-09T10:00:00");
        assertThat(json.get("version").asLong()).isEqualTo(1L);
        assertThat(json.has("movementCode")).isFalse();
        assertThat(json.get("account_code").asText()).isEqualTo(accountCode.toString());
        assertThat(json.has("accountCode")).isFalse();
    }

    @Test
    void shouldDeserializePositiveSettlementAmountAndEffectiveDate() throws Exception {
        final MovementSettlementRequest request = objectMapper.readValue(
                "{\"amount\":12.50,\"effective_date\":\"2026-11-08\"}",
                MovementSettlementRequest.class);

        assertThat(request.amount()).isEqualByComparingTo("12.50");
        assertThat(request.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void shouldDeserializeOptionalAccountCodeAndDefaultToNullWhenOmitted() throws Exception {
        final UUID accountCode = UUID.randomUUID();

        final MovementSettlementRequest withAccount = objectMapper.readValue(
                "{\"amount\":1.00,\"effective_date\":\"2026-11-08\",\"account_code\":\"" + accountCode + "\"}",
                MovementSettlementRequest.class);
        final MovementSettlementRequest withoutAccount = objectMapper.readValue(
                "{\"amount\":1.00,\"effective_date\":\"2026-11-08\"}", MovementSettlementRequest.class);

        assertThat(withAccount.accountCode()).isEqualTo(accountCode);
        assertThat(withoutAccount.accountCode()).isNull();
    }
}
