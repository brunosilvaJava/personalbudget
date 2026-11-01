package com.bts.personalbudget.controller.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailyBalanceResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSerializeDailyBalanceInSnakeCase() {
        final DailyBalanceResponse response = new DailyBalanceResponse(
                LocalDate.of(2026, 11, 1),
                new DailyBalanceResponse.Balance(
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE.negate(), BigDecimal.TEN),
                new DailyBalanceResponse.Projected(
                        BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE.negate(), BigDecimal.TEN));

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.has("date")).isTrue();
        assertThat(fieldNames(json.get("balance")))
                .containsExactlyInAnyOrder("opening", "total_revenue", "total_expense", "closing");
        assertThat(fieldNames(json.get("projected")))
                .containsExactlyInAnyOrder("opening", "pending_total_revenue", "pending_total_expense", "closing");
    }

    static java.util.List<String> fieldNames(final JsonNode node) {
        final java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
