package com.bts.personalbudget.controller.categorysummary;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import com.bts.personalbudget.mapper.CategorySummaryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySummaryResponseTest {

    private static final UUID GROUP_CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_CODE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final CategorySummaryMapper mapper = CategorySummaryMapper.INSTANCE;

    @Test
    void shouldMapEveryLevelOfTheSummary() {
        final CategorySummary.Line rent = new CategorySummary.Line(CATEGORY_CODE, "Aluguel", CategoryStatus.ACTIVE,
                new BigDecimal("-1000.00"), new BigDecimal("-1500.00"),
                new BigDecimal("50.00"), new BigDecimal("60.00"));
        final CategorySummary.Group home = new CategorySummary.Group(GROUP_CODE, "Residência", 1,
                new BigDecimal("-1000.00"), new BigDecimal("-1500.00"),
                new BigDecimal("50.00"), new BigDecimal("60.00"), List.of(rent));
        final CategorySummary summary = new CategorySummary(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                OperationType.DEBIT, new BigDecimal("-2000.00"), new BigDecimal("-2500.00"), List.of(home));

        final CategorySummaryResponse response = mapper.toResponse(summary);

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(response.operationType()).isEqualTo(OperationType.DEBIT);
        assertThat(response.total()).isEqualByComparingTo("-2000.00");
        assertThat(response.projectedTotal()).isEqualByComparingTo("-2500.00");

        final CategorySummaryGroupResponse group = response.groups().get(0);
        assertThat(group.code()).isEqualTo(GROUP_CODE);
        assertThat(group.name()).isEqualTo("Residência");
        assertThat(group.displayOrder()).isEqualTo(1);
        assertThat(group.total()).isEqualByComparingTo("-1000.00");
        assertThat(group.projectedTotal()).isEqualByComparingTo("-1500.00");
        assertThat(group.percent()).isEqualByComparingTo("50.00");
        assertThat(group.projectedPercent()).isEqualByComparingTo("60.00");

        final CategorySummaryLineResponse line = group.categories().get(0);
        assertThat(line.code()).isEqualTo(CATEGORY_CODE);
        assertThat(line.name()).isEqualTo("Aluguel");
        assertThat(line.status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(line.total()).isEqualByComparingTo("-1000.00");
        assertThat(line.projectedTotal()).isEqualByComparingTo("-1500.00");
        assertThat(line.percent()).isEqualByComparingTo("50.00");
        assertThat(line.projectedPercent()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldMapUncategorizedRowWithNullCodeAndStatus() {
        final CategorySummary.Line line = new CategorySummary.Line(null, "Sem categoria", null,
                BigDecimal.ZERO, new BigDecimal("-500.00"), BigDecimal.ZERO, new BigDecimal("100.00"));
        final CategorySummary.Group group = new CategorySummary.Group(null, "Sem categoria", null,
                BigDecimal.ZERO, new BigDecimal("-500.00"), BigDecimal.ZERO, new BigDecimal("100.00"), List.of(line));
        final CategorySummary summary = new CategorySummary(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                OperationType.DEBIT, BigDecimal.ZERO, new BigDecimal("-500.00"), List.of(group));

        final CategorySummaryResponse response = mapper.toResponse(summary);

        assertThat(response.groups().get(0).code()).isNull();
        assertThat(response.groups().get(0).displayOrder()).isNull();
        assertThat(response.groups().get(0).categories().get(0).code()).isNull();
        assertThat(response.groups().get(0).categories().get(0).status()).isNull();
    }

    @Test
    void shouldSerializeEveryLevelInSnakeCase() {
        final CategorySummaryLineResponse line = new CategorySummaryLineResponse(CATEGORY_CODE, "Aluguel",
                CategoryStatus.ACTIVE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        final CategorySummaryGroupResponse group = new CategorySummaryGroupResponse(GROUP_CODE, "Residência", 1,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, List.of(line));
        final CategorySummaryResponse response = new CategorySummaryResponse(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), OperationType.DEBIT, BigDecimal.ONE, BigDecimal.ONE, List.of(group));

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "start_date", "end_date", "operation_type", "total", "projected_total", "groups");
        assertThat(fieldNames(json.get("groups").get(0))).containsExactlyInAnyOrder(
                "code", "name", "display_order", "total", "projected_total", "percent", "projected_percent",
                "categories");
        assertThat(fieldNames(json.get("groups").get(0).get("categories").get(0))).containsExactlyInAnyOrder(
                "code", "name", "status", "total", "projected_total", "percent", "projected_percent");
        assertThat(json.get("start_date").asText()).isEqualTo("2026-11-01");
    }

    private List<String> fieldNames(final JsonNode json) {
        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
