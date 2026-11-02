package com.bts.personalbudget.controller.paycycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending.Type;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
                "{\"expected_version\": 7}", PayCycleDeactivationRequest.class);

        assertThat(request.expectedVersion()).isEqualTo(7L);
        assertThat(validator.validate(request)).isEmpty();
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
        assertThat(violatedFields(new PayCycleDeactivationRequest(null))).containsExactly("expectedVersion");
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
