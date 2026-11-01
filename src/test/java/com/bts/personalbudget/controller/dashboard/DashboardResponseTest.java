package com.bts.personalbudget.controller.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSerializeDashboardInSnakeCase() {
        final LocalDate day = LocalDate.of(2026, 11, 1);
        final DashboardResponse response = new DashboardResponse(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE.negate(), BigDecimal.ONE.negate(),
                true, day, BigDecimal.ZERO, day,
                day, day,
                30, 1, 29,
                BigDecimal.ONE, BigDecimal.ONE);

        final JsonNode json = objectMapper.valueToTree(response);

        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(
                "current_balance",
                "current_projected_balance",
                "month_closing_balance",
                "month_projected_closing_balance",
                "month_total_revenue",
                "month_projected_total_revenue",
                "month_total_expense",
                "month_projected_total_expense",
                "has_negative_balance_alert",
                "first_negative_balance_date",
                "lowest_projected_balance",
                "lowest_balance_date",
                "reference_month",
                "current_date",
                "total_days_in_month",
                "days_elapsed",
                "days_remaining",
                "month_net_change",
                "projected_net_change");
        assertThat(json.get("has_negative_balance_alert").asBoolean()).isTrue();
    }
}
