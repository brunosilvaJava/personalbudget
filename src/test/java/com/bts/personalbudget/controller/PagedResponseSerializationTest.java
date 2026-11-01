package com.bts.personalbudget.controller;

import com.bts.personalbudget.controller.financialmovement.PagedFinancialMovementResponse;
import com.bts.personalbudget.controller.fixedbill.PagedFixedBillResponse;
import com.bts.personalbudget.controller.installmentbill.PagedInstallmentBillResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseSerializationTest {

    private static final String[] PAGE_FIELDS =
            {"content", "page", "size", "total_elements", "total_pages", "first", "last"};

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReturnPagedMovementsWithSnakeCaseMetadata() {
        assertThat(fieldNames(new PagedFinancialMovementResponse(List.of(), 0, 10, 25L, 3, true, false)))
                .containsExactlyInAnyOrder(PAGE_FIELDS);
    }

    @Test
    void shouldReturnPagedFixedBillsWithSnakeCaseMetadata() {
        assertThat(fieldNames(new PagedFixedBillResponse(List.of(), 0, 10, 25L, 3, true, false)))
                .containsExactlyInAnyOrder(PAGE_FIELDS);
    }

    @Test
    void shouldReturnPagedInstallmentBillsWithSnakeCaseMetadata() {
        assertThat(fieldNames(new PagedInstallmentBillResponse(List.of(), 0, 10, 25L, 3, true, false)))
                .containsExactlyInAnyOrder(PAGE_FIELDS);
    }

    private List<String> fieldNames(final Object response) {
        final JsonNode json = objectMapper.valueToTree(response);
        final List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
