package com.bts.personalbudget.controller.category;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySerializationTest {

    private static final UUID GROUP_CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldSerializeCategoryInSnakeCase() {
        final CategoryResponse response = new CategoryResponse(
                UUID.randomUUID(), "Aluguel", OperationType.DEBIT, CategoryStatus.ACTIVE, GROUP_CODE, "Residência");

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "code", "name", "operation_type", "status", "group_code", "group_name");
    }

    @Test
    void shouldSerializeCategoryGroupInSnakeCase() {
        final CategoryGroupResponse response = new CategoryGroupResponse(GROUP_CODE, "Residência", 1);

        assertThat(fieldNames(response)).containsExactlyInAnyOrder("code", "name", "display_order");
    }

    @Test
    void shouldReadCategoryRequestFromSnakeCase() throws Exception {
        final String json = """
                {"name": "Aluguel", "operation_type": "DEBIT", "group_code": "11111111-1111-1111-1111-111111111111"}
                """;

        final CategoryRequest request = objectMapper.readValue(json, CategoryRequest.class);

        assertThat(request.name()).isEqualTo("Aluguel");
        assertThat(request.operationType()).isEqualTo(OperationType.DEBIT);
        assertThat(request.groupCode()).isEqualTo(GROUP_CODE);
    }

    @Test
    void shouldRejectCategoryRequestWhenRequiredFieldsAreMissing() {
        final CategoryRequest request = new CategoryRequest(" ", null, null);

        assertThat(validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .distinct())
                .containsExactlyInAnyOrder("name", "operationType", "groupCode");
    }

    @Test
    void shouldRejectCategoryRequestWhenNameIsTooLong() {
        final CategoryRequest request = new CategoryRequest("a".repeat(51), OperationType.DEBIT, GROUP_CODE);

        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    void shouldAcceptCategoryUpdateRequestWithoutFields() {
        assertThat(validator.validate(new CategoryUpdateRequest(null, null))).isEmpty();
    }

    private List<String> fieldNames(final Object response) {
        final JsonNode json = objectMapper.valueToTree(response);
        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
