package com.bts.personalbudget.controller.incomeschedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.controller.holidaycalendar.HolidayChangeImpactResponse;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncomeScheduleSerializationTest {

    private static final UUID CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_CODE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // Spring Boot turns WRITE_DATES_AS_TIMESTAMPS off; a bare ObjectMapper has to be told the same.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldSerializeIncomeScheduleInSnakeCase() {
        final IncomeScheduleResponse response = new IncomeScheduleResponse(
                CODE, "Salário", new BigDecimal("5000.00"), CATEGORY_CODE, DateRuleType.FIXED_DAY, 5,
                BusinessWeek.MONDAY_TO_FRIDAY, NonBusinessDayPolicy.POSTPONE, YearMonth.of(2026, 12), null,
                true, IncomeScheduleStatus.ACTIVE, null);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "code", "description", "amount", "category_code", "rule_type", "rule_value", "business_week",
                "non_business_day_policy", "start_month", "end_month", "anchor", "status",
                "replaces_fixed_bill_code");
    }

    @Test
    void shouldSerializeMonthsAsYearMonthText() {
        final IncomeScheduleResponse response = new IncomeScheduleResponse(
                CODE, "Salário", new BigDecimal("5000.00"), null, DateRuleType.NTH_BUSINESS_DAY, 5,
                BusinessWeek.MONDAY_TO_FRIDAY, null, YearMonth.of(2026, 12), YearMonth.of(2027, 11), false,
                IncomeScheduleStatus.INACTIVE, null);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("start_month").asText()).isEqualTo("2026-12");
        assertThat(json.get("end_month").asText()).isEqualTo("2027-11");
        assertThat(json.get("non_business_day_policy").isNull()).isTrue();
        assertThat(json.get("anchor").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldSerializeOccurrenceInSnakeCase() {
        final IncomeOccurrenceResponse response = new IncomeOccurrenceResponse(
                YearMonth.of(2026, 12), State.PROJECTED, LocalDate.of(2026, 12, 7), new BigDecimal("5000.00"),
                null, null);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "nominal_month", "state", "planned_date", "amount", "movement_code", "pending_year");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("nominal_month").asText()).isEqualTo("2026-12");
        assertThat(json.get("planned_date").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("state").asText()).isEqualTo("PROJECTED");
    }

    @Test
    void shouldSerializeAnOccurrenceWithoutDateAsNullsAndThePendingYear() {
        final IncomeOccurrenceResponse response = new IncomeOccurrenceResponse(
                YearMonth.of(2027, 1), State.CALENDAR_NOT_REVIEWED, null, null, null, 2027);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("planned_date").isNull()).isTrue();
        assertThat(json.get("amount").isNull()).isTrue();
        assertThat(json.get("pending_year").asInt()).isEqualTo(2027);
        assertThat(json.get("state").asText()).isEqualTo("CALENDAR_NOT_REVIEWED");
    }

    @Test
    void shouldSerializeCalendarImpactInSnakeCase() {
        final IncomeOccurrenceResponse before = new IncomeOccurrenceResponse(
                YearMonth.of(2026, 8), State.PROJECTED, LocalDate.of(2026, 8, 5), new BigDecimal("5000.00"),
                null, null);
        final IncomeOccurrenceResponse after = new IncomeOccurrenceResponse(
                YearMonth.of(2026, 8), State.PROJECTED, LocalDate.of(2026, 8, 6), new BigDecimal("5000.00"),
                null, null);
        final HolidayChangeImpactResponse response = new HolidayChangeImpactResponse(
                CODE, "Salário", YearMonth.of(2026, 8), before, after);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "schedule_code", "schedule_description", "nominal_month", "before", "after");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("before").get("planned_date").asText()).isEqualTo("2026-08-05");
        assertThat(json.get("after").get("planned_date").asText()).isEqualTo("2026-08-06");
    }

    @Test
    void shouldReadRequestFromSnakeCase() throws Exception {
        final String json = """
                {
                  "description": "Salário",
                  "amount": 5000.00,
                  "category_code": "22222222-2222-2222-2222-222222222222",
                  "rule_type": "NTH_BUSINESS_DAY",
                  "rule_value": 5,
                  "business_week": "MONDAY_TO_SATURDAY",
                  "start_month": "2026-12",
                  "end_month": "2027-11",
                  "anchor": true,
                  "replaces_fixed_bill_code": "11111111-1111-1111-1111-111111111111"
                }
                """;

        final IncomeScheduleRequest request = objectMapper.readValue(json, IncomeScheduleRequest.class);

        assertThat(request.description()).isEqualTo("Salário");
        assertThat(request.amount()).isEqualByComparingTo("5000.00");
        assertThat(request.categoryCode()).isEqualTo(CATEGORY_CODE);
        assertThat(request.ruleType()).isEqualTo(DateRuleType.NTH_BUSINESS_DAY);
        assertThat(request.ruleValue()).isEqualTo(5);
        assertThat(request.businessWeek()).isEqualTo(BusinessWeek.MONDAY_TO_SATURDAY);
        assertThat(request.nonBusinessDayPolicy()).isNull();
        assertThat(request.startMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(request.endMonth()).isEqualTo(YearMonth.of(2027, 11));
        assertThat(request.anchor()).isTrue();
        assertThat(request.replacesFixedBillCode()).isEqualTo(CODE);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldReadRequestWithoutTheOptionalFields() throws Exception {
        final String json = """
                {"description": "Salário", "amount": 5000, "rule_type": "FIXED_DAY", "rule_value": 5,
                 "business_week": "MONDAY_TO_FRIDAY", "non_business_day_policy": "ANTICIPATE",
                 "start_month": "2026-12"}
                """;

        final IncomeScheduleRequest request = objectMapper.readValue(json, IncomeScheduleRequest.class);

        assertThat(request.categoryCode()).isNull();
        assertThat(request.endMonth()).isNull();
        assertThat(request.anchor()).isNull();
        assertThat(request.replacesFixedBillCode()).isNull();
        assertThat(request.nonBusinessDayPolicy()).isEqualTo(NonBusinessDayPolicy.ANTICIPATE);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectRequestWhenRequiredFieldsAreMissing() {
        final IncomeScheduleRequest request = new IncomeScheduleRequest(
                " ", null, null, null, null, null, null, null, null, null, null);

        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "description", "amount", "ruleType", "ruleValue", "businessWeek", "startMonth");
    }

    @Test
    void shouldRejectRequestWhenDescriptionIsOutsideTheLimits() {
        assertThat(violatedFields(request("ab", new BigDecimal("5000.00")))).containsExactly("description");
        assertThat(violatedFields(request("a".repeat(51), new BigDecimal("5000.00")))).containsExactly("description");
        assertThat(violatedFields(request("abc", new BigDecimal("5000.00")))).isEmpty();
    }

    @Test
    void shouldRejectRequestWhenAmountIsNotAPositiveValueThatFitsTheColumn() {
        // DECIMAL(10,2): at most 8 integer digits and 2 decimals.
        assertThat(violatedFields(request("Salário", BigDecimal.ZERO))).containsExactly("amount");
        assertThat(violatedFields(request("Salário", new BigDecimal("-1.00")))).containsExactly("amount");
        assertThat(violatedFields(request("Salário", new BigDecimal("10.001")))).containsExactly("amount");
        assertThat(violatedFields(request("Salário", new BigDecimal("123456789.00")))).containsExactly("amount");
        assertThat(violatedFields(request("Salário", new BigDecimal("12345678.99")))).isEmpty();
    }

    @Test
    void shouldAcceptUpdateRequestWithoutFields() {
        final IncomeScheduleUpdateRequest request = new IncomeScheduleUpdateRequest(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectUpdateRequestWithInvalidDescriptionAndAmount() {
        final IncomeScheduleUpdateRequest request = new IncomeScheduleUpdateRequest(
                "ab", new BigDecimal("0.00"), null, null, null, null, null, null, null, null);

        assertThat(violatedFields(request)).containsExactlyInAnyOrder("description", "amount");
    }

    @Test
    void shouldReadUpdateRequestToRemoveTheEndMonth() throws Exception {
        final IncomeScheduleUpdateRequest request = objectMapper.readValue(
                "{\"remove_end_month\": true, \"end_month\": null}", IncomeScheduleUpdateRequest.class);

        assertThat(request.removeEndMonth()).isTrue();
        assertThat(request.endMonth()).isNull();
    }

    private IncomeScheduleRequest request(final String description, final BigDecimal amount) {
        return new IncomeScheduleRequest(description, amount, null, DateRuleType.FIXED_DAY, 5,
                BusinessWeek.MONDAY_TO_FRIDAY, NonBusinessDayPolicy.POSTPONE, YearMonth.of(2026, 12), null, null,
                null);
    }

    private List<String> violatedFields(final Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .distinct()
                .toList();
    }

    private List<String> fieldNames(final Object response) {
        final JsonNode json = objectMapper.valueToTree(response);
        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
