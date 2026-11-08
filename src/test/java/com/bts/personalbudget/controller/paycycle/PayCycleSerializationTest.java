package com.bts.personalbudget.controller.paycycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending.Type;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleSerializationTest {

    private static final UUID CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SCHEDULE_CODE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // Spring Boot turns WRITE_DATES_AS_TIMESTAMPS off; a bare ObjectMapper has to be told the same.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldSerializeACycleInSnakeCase() {
        final PayCycleResponse response = cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "code", "start_date", "end_date_exclusive", "last_day", "total_days", "label", "reference_month",
                "kind", "status", "anchor_schedule_code", "start_adjusted", "end_adjusted", "version", "pending");
    }

    @Test
    void shouldSerializeDatesAndMonthsAsText() {
        final JsonNode json = objectMapper.valueToTree(cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of()));

        assertThat(json.get("code").asText()).isEqualTo(CODE.toString());
        assertThat(json.get("start_date").asText()).isEqualTo("2026-11-05");
        assertThat(json.get("end_date_exclusive").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("last_day").asText()).isEqualTo("2026-12-06");
        assertThat(json.get("total_days").asInt()).isEqualTo(32);
        assertThat(json.get("reference_month").asText()).isEqualTo("2026-11");
        assertThat(json.get("label").asText()).isEqualTo("Nov/Dez");
        assertThat(json.get("kind").asText()).isEqualTo("REGULAR");
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
        assertThat(json.get("anchor_schedule_code").asText()).isEqualTo(SCHEDULE_CODE.toString());
        assertThat(json.get("start_adjusted").asBoolean()).isFalse();
        assertThat(json.get("end_adjusted").asBoolean()).isFalse();
        assertThat(json.get("version").asLong()).isEqualTo(0L);
        assertThat(json.get("pending").isArray()).isTrue();
    }

    @Test
    void shouldSerializeATransitionWithoutReferenceAsNulls() {
        final JsonNode json = objectMapper.valueToTree(cycle(null, null, List.of()));

        assertThat(json.get("reference_month").isNull()).isTrue();
        assertThat(json.get("anchor_schedule_code").isNull()).isTrue();
    }

    @Test
    void shouldSerializeAPendencyInSnakeCase() {
        final PayCyclePendingResponse response = new PayCyclePendingResponse(
                Type.BOUNDARY_DIVERGES_FROM_ANCHOR, YearMonth.of(2026, 12), null, CODE,
                LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8));

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "type", "nominal_month", "year", "cycle_code", "boundary_date", "anchor_date");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("type").asText()).isEqualTo("BOUNDARY_DIVERGES_FROM_ANCHOR");
        assertThat(json.get("nominal_month").asText()).isEqualTo("2026-12");
        assertThat(json.get("year").isNull()).isTrue();
        assertThat(json.get("cycle_code").asText()).isEqualTo(CODE.toString());
        assertThat(json.get("boundary_date").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("anchor_date").asText()).isEqualTo("2026-12-08");
    }

    @Test
    void shouldSerializeACalendarPendencyWithTheYear() {
        final PayCyclePendingResponse response = new PayCyclePendingResponse(
                Type.CALENDAR_NOT_REVIEWED, YearMonth.of(2027, 1), 2027, null, null, null);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("year").asInt()).isEqualTo(2027);
        assertThat(json.get("cycle_code").isNull()).isTrue();
    }

    @Test
    void shouldSerializeTheConfigurationInSnakeCase() {
        final PayCycleConfigResponse response = new PayCycleConfigResponse(
                PayCycleMode.PAY_CYCLE, SCHEDULE_CODE, LocalDate.of(2026, 11, 5), 3L, List.of());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "mode", "anchor_schedule_code", "activation_date", "version", "pending");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("mode").asText()).isEqualTo("PAY_CYCLE");
        assertThat(json.get("activation_date").asText()).isEqualTo("2026-11-05");
        assertThat(json.get("version").asLong()).isEqualTo(3L);
    }

    @Test
    void shouldSerializeTheConfigurationInCalendarMonthModeWithNulls() {
        final PayCycleConfigResponse response = new PayCycleConfigResponse(
                PayCycleMode.CALENDAR_MONTH, null, null, 0L, List.of());

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("anchor_schedule_code").isNull()).isTrue();
        assertThat(json.get("activation_date").isNull()).isTrue();
    }

    @Test
    void shouldSerializeThePreviewWithTheVersionToSendBack() {
        final PayCycleActivationPreviewResponse response = new PayCycleActivationPreviewResponse(
                2L, List.of(cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of())), List.of());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("expected_version", "cycles", "pending");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("expected_version").asLong()).isEqualTo(2L);
        assertThat(json.get("cycles").size()).isEqualTo(1);
    }

    @Test
    void shouldSerializeTheAnswerForNoCurrentCycle() {
        final PayCycleCurrentNotFoundResponse response = new PayCycleCurrentNotFoundResponse(
                "Nenhum ciclo em andamento", List.of());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("message", "pending");
    }

    @Test
    void shouldReadThePreviewRequestFromSnakeCase() throws Exception {
        final PayCycleActivationPreviewRequest request = objectMapper.readValue(
                "{\"initial_date\": \"2026-11-05\"}", PayCycleActivationPreviewRequest.class);

        assertThat(request.initialDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldReadTheActivationRequestFromSnakeCase() throws Exception {
        final PayCycleActivationRequest request = objectMapper.readValue(
                "{\"initial_date\": \"2026-11-05\", \"expected_version\": 4}", PayCycleActivationRequest.class);

        assertThat(request.initialDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(request.expectedVersion()).isEqualTo(4L);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldReadTheDeactivationRequestFromSnakeCase() throws Exception {
        final PayCycleDeactivationRequest request = objectMapper.readValue(
                "{\"expected_version\": 7, \"justification\": \"Mudei de emprego\"}", PayCycleDeactivationRequest.class);

        assertThat(request.expectedVersion()).isEqualTo(7L);
        assertThat(request.justification()).isEqualTo("Mudei de emprego");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldReadTheEndAdjustmentPreviewRequestFromSnakeCase() throws Exception {
        final PayCycleEndAdjustmentPreviewRequest request = objectMapper.readValue(
                "{\"new_end_date\": \"2026-12-04\"}", PayCycleEndAdjustmentPreviewRequest.class);

        assertThat(request.newEndDate()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldReadTheEndAdjustmentRequestFromSnakeCase() throws Exception {
        final PayCycleEndAdjustmentRequest request = objectMapper.readValue(
                "{\"new_end_date\": \"2026-12-04\", \"justification\": \"Salário caiu antes do feriado\","
                        + " \"expected_version\": 2, \"expected_next_version\": 5}",
                PayCycleEndAdjustmentRequest.class);

        assertThat(request.newEndDate()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(request.justification()).isEqualTo("Salário caiu antes do feriado");
        assertThat(request.expectedVersion()).isEqualTo(2L);
        assertThat(request.expectedNextVersion()).isEqualTo(5L);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldSerializeClosurePreviewAndSnapshotInSnakeCase() {
        final PayCycleSummaryResponse summary = new PayCycleSummaryResponse(
                cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of()),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, List.of(), List.of());
        final PayCycleClosurePreviewResponse preview = new PayCycleClosurePreviewResponse(
                summary, 4L, true, List.of(), List.of(CODE));
        final PayCycleSnapshotResponse snapshot = new PayCycleSnapshotResponse(
                CODE, SCHEDULE_CODE, 2, 5L, Instant.parse("2026-11-02T21:35:00Z"),
                new BigDecimal("500.00"), new BigDecimal("100.00"),
                new BigDecimal("600.00"), new BigDecimal("200.00"),
                List.of(new PayCycleSnapshotResponse.Category(
                        null, "Sem categoria", OperationType.DEBIT, new BigDecimal("100.00"),
                        new BigDecimal("200.00"))));

        assertThat(fieldNames(preview)).containsExactlyInAnyOrder(
                "summary", "expected_version", "can_close", "blockers", "late_movement_codes");
        assertThat(fieldNames(snapshot)).containsExactlyInAnyOrder(
                "code", "cycle_code", "snapshot_version", "cycle_version", "closed_at",
                "recognized_revenue", "recognized_expense", "projected_revenue", "projected_expense",
                "categories");
        final JsonNode previewJson = objectMapper.valueToTree(preview);
        assertThat(previewJson.get("expected_version").asLong()).isEqualTo(4L);
        assertThat(previewJson.get("can_close").asBoolean()).isTrue();
        assertThat(previewJson.get("late_movement_codes").get(0).asText()).isEqualTo(CODE.toString());
        final JsonNode snapshotJson = objectMapper.valueToTree(snapshot);
        assertThat(snapshotJson.get("closed_at").asText()).isEqualTo("2026-11-02T21:35:00Z");
        assertThat(snapshotJson.get("categories").get(0).get("category_name").asText()).isEqualTo("Sem categoria");
    }

    @Test
    void shouldReadClosureAndReopenRequestsFromSnakeCaseAndRequireVersions() throws Exception {
        final PayCycleClosureRequest close = objectMapper.readValue(
                "{\"expected_version\": 4}", PayCycleClosureRequest.class);
        final PayCycleReopenRequest reopen = objectMapper.readValue(
                "{\"expected_version\": 5}", PayCycleReopenRequest.class);

        assertThat(close.expectedVersion()).isEqualTo(4L);
        assertThat(reopen.expectedVersion()).isEqualTo(5L);
        assertThat(validator.validate(close)).isEmpty();
        assertThat(validator.validate(reopen)).isEmpty();
        assertThat(violatedFields(new PayCycleClosureRequest(null))).containsExactly("expectedVersion");
        assertThat(violatedFields(new PayCycleReopenRequest(null))).containsExactly("expectedVersion");
    }

    @Test
    void shouldRejectAnEndAdjustmentPreviewRequestWithoutTheDate() {
        assertThat(violatedFields(new PayCycleEndAdjustmentPreviewRequest(null))).containsExactly("newEndDate");
    }

    @Test
    void shouldRejectAnEndAdjustmentRequestWithNothingFilled() {
        assertThat(violatedFields(new PayCycleEndAdjustmentRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("newEndDate", "justification", "expectedVersion", "expectedNextVersion");
    }

    @Test
    void shouldRejectAnEndAdjustmentRequestWithoutTheNextVersion() {
        assertThat(violatedFields(new PayCycleEndAdjustmentRequest(LocalDate.of(2026, 12, 4), "Motivo", 2L, null)))
                .containsExactly("expectedNextVersion");
    }

    @Test
    void shouldRejectABlankJustification() {
        assertThat(violatedFields(new PayCycleEndAdjustmentRequest(LocalDate.of(2026, 12, 4), "   ", 2L, 5L)))
                .containsExactly("justification");
        assertThat(violatedFields(new PayCycleDeactivationRequest(7L, "")))
                .containsExactly("justification");
    }

    @Test
    void shouldRejectAJustificationLongerThanTheHistoryStores() {
        final String tooLong = "x".repeat(501);

        assertThat(violatedFields(new PayCycleEndAdjustmentRequest(LocalDate.of(2026, 12, 4), tooLong, 2L, 5L)))
                .containsExactly("justification");
        assertThat(violatedFields(new PayCycleDeactivationRequest(7L, tooLong))).containsExactly("justification");
        assertThat(violatedFields(new PayCycleDeactivationRequest(7L, "x".repeat(500)))).isEmpty();
    }

    @Test
    void shouldSerializeAnAdjustmentOfTheHistoryInSnakeCase() {
        final PayCycleAdjustmentResponse response = new PayCycleAdjustmentResponse(
                CODE, PayCycleAdjustmentType.MANUAL_BOUNDARY, CODE, SCHEDULE_CODE, LocalDate.of(2026, 12, 7),
                LocalDate.of(2026, 12, 4), "Salário caiu antes do feriado", 2L, 5L, null,
                Instant.parse("2026-11-02T21:35:00Z"));

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "code", "type", "previous_cycle_code", "next_cycle_code", "old_boundary", "new_boundary",
                "justification", "expected_previous_version", "expected_next_version", "expected_config_version",
                "created_date");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("type").asText()).isEqualTo("MANUAL_BOUNDARY");
        assertThat(json.get("previous_cycle_code").asText()).isEqualTo(CODE.toString());
        assertThat(json.get("next_cycle_code").asText()).isEqualTo(SCHEDULE_CODE.toString());
        assertThat(json.get("old_boundary").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("new_boundary").asText()).isEqualTo("2026-12-04");
        assertThat(json.get("expected_previous_version").asLong()).isEqualTo(2L);
        assertThat(json.get("expected_next_version").asLong()).isEqualTo(5L);
        assertThat(json.get("expected_config_version").isNull()).isTrue();
        assertThat(json.get("created_date").asText()).isEqualTo("2026-11-02T21:35:00Z");
    }

    @Test
    void shouldSerializeADeactivationWithoutTheBoundaryItDidNotMove() {
        final PayCycleAdjustmentResponse response = new PayCycleAdjustmentResponse(
                CODE, PayCycleAdjustmentType.DEACTIVATION, CODE, null, LocalDate.of(2026, 12, 7), null,
                "Mudei de emprego", null, null, 3L, Instant.parse("2026-11-02T21:35:00Z"));

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("type").asText()).isEqualTo("DEACTIVATION");
        assertThat(json.get("next_cycle_code").isNull()).isTrue();
        assertThat(json.get("new_boundary").isNull()).isTrue();
        assertThat(json.get("expected_config_version").asLong()).isEqualTo(3L);
    }

    @Test
    void shouldSerializeTheMovementThatChangesCycleInSnakeCase() {
        final PayCycleMovementImpactResponse response = new PayCycleMovementImpactResponse(
                CODE, "Aluguel", OperationType.DEBIT, new BigDecimal("1500.00"), FinancialMovementStatus.PENDING,
                LocalDate.of(2026, 12, 5), SCHEDULE_CODE);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "code", "description", "operation_type", "amount", "status", "competence_date", "category_code");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("operation_type").asText()).isEqualTo("DEBIT");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(json.get("status").asText()).isEqualTo("PENDING");
        assertThat(json.get("competence_date").asText()).isEqualTo("2026-12-05");
    }

    @Test
    void shouldSerializeTheIncomeOccurrenceThatChangesCycleInSnakeCase() {
        final PayCycleIncomeImpactResponse response = new PayCycleIncomeImpactResponse(
                SCHEDULE_CODE, "Salário", YearMonth.of(2026, 12), State.PROJECTED, LocalDate.of(2026, 12, 7),
                new BigDecimal("5000.00"), null);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "schedule_code", "schedule_description", "nominal_month", "state", "planned_date", "amount",
                "movement_code");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("nominal_month").asText()).isEqualTo("2026-12");
        assertThat(json.get("state").asText()).isEqualTo("PROJECTED");
        assertThat(json.get("planned_date").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("movement_code").isNull()).isTrue();
    }

    @Test
    void shouldSerializeTheEndAdjustmentPreviewWithTheVersionsToSendBack() {
        final PayCycleEndAdjustmentPreviewResponse response = new PayCycleEndAdjustmentPreviewResponse(
                LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 4), 2L, 5L,
                List.of(cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of())),
                List.of(cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of())),
                SCHEDULE_CODE, CODE, List.of(), List.of());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "old_end_date", "new_end_date", "expected_version", "expected_next_version", "cycles_before",
                "cycles_after", "leaving_cycle_code", "receiving_cycle_code", "movements", "income_occurrences");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("old_end_date").asText()).isEqualTo("2026-12-07");
        assertThat(json.get("new_end_date").asText()).isEqualTo("2026-12-04");
        assertThat(json.get("expected_version").asLong()).isEqualTo(2L);
        assertThat(json.get("expected_next_version").asLong()).isEqualTo(5L);
        assertThat(json.get("cycles_before").size()).isEqualTo(1);
        assertThat(json.get("movements").isArray()).isTrue();
        assertThat(json.get("income_occurrences").isArray()).isTrue();
    }

    @Test
    void shouldSerializeTheEndAdjustmentAnswerWithBothCycles() {
        final PayCycleAdjustmentResponse adjustment = new PayCycleAdjustmentResponse(
                CODE, PayCycleAdjustmentType.MANUAL_BOUNDARY, CODE, SCHEDULE_CODE, LocalDate.of(2026, 12, 7),
                LocalDate.of(2026, 12, 4), "Motivo", 2L, 5L, null, null);
        final PayCycleEndAdjustmentResponse response = new PayCycleEndAdjustmentResponse(
                adjustment, cycle(YearMonth.of(2026, 11), SCHEDULE_CODE, List.of()),
                cycle(YearMonth.of(2026, 12), SCHEDULE_CODE, List.of()));

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("adjustment", "cycle", "next_cycle");
        final JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("adjustment").get("type").asText()).isEqualTo("MANUAL_BOUNDARY");
        assertThat(json.get("next_cycle").get("reference_month").asText()).isEqualTo("2026-12");
    }

    @Test
    void shouldRejectAPreviewRequestWithoutTheInitialDate() {
        assertThat(violatedFields(new PayCycleActivationPreviewRequest(null))).containsExactly("initialDate");
    }

    @Test
    void shouldRejectAnActivationRequestWithoutTheDateAndTheVersion() {
        assertThat(violatedFields(new PayCycleActivationRequest(null, null)))
                .containsExactlyInAnyOrder("initialDate", "expectedVersion");
    }

    @Test
    void shouldRejectAnActivationRequestWithoutTheVersion() {
        assertThat(violatedFields(new PayCycleActivationRequest(LocalDate.of(2026, 11, 5), null)))
                .containsExactly("expectedVersion");
    }

    @Test
    void shouldRejectADeactivationRequestWithoutTheVersion() {
        assertThat(violatedFields(new PayCycleDeactivationRequest(null, "Motivo"))).containsExactly("expectedVersion");
    }

    @Test
    void shouldRejectADeactivationRequestWithoutAJustification() {
        assertThat(violatedFields(new PayCycleDeactivationRequest(7L, null))).containsExactly("justification");
        assertThat(violatedFields(new PayCycleDeactivationRequest(null, null)))
                .containsExactlyInAnyOrder("expectedVersion", "justification");
    }

    @Test
    void shouldReadAnEmptyBodyAsRequestWithNullsThatValidationRejects() throws Exception {
        final PayCycleActivationRequest request = objectMapper.readValue("{}", PayCycleActivationRequest.class);

        assertThat(request.initialDate()).isNull();
        assertThat(request.expectedVersion()).isNull();
        assertThat(violatedFields(request)).containsExactlyInAnyOrder("initialDate", "expectedVersion");
    }

    private PayCycleResponse cycle(final YearMonth referenceMonth, final UUID anchorScheduleCode,
                                   final List<PayCyclePendingResponse> pending) {
        return new PayCycleResponse(CODE, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 7),
                LocalDate.of(2026, 12, 6), 32, "Nov/Dez", referenceMonth, PayCycleKind.REGULAR, PayCycleStatus.OPEN,
                anchorScheduleCode, false, false, 0L, pending);
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
