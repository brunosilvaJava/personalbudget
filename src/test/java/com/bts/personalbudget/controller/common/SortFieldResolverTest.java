package com.bts.personalbudget.controller.common;

import com.bts.personalbudget.exception.InvalidFieldsException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SortFieldResolverTest {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "description", "description",
            "movement_date", "movementDate");

    @Test
    void shouldResolveSnakeCaseSortFieldToEntityProperty() {
        final Sort sort = SortFieldResolver.resolve("movement_date", "ASC", SORT_FIELDS);

        final Sort.Order order = sort.getOrderFor("movementDate");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void shouldSortDescendingWhenDirectionIsNotAsc() {
        final Sort sort = SortFieldResolver.resolve("description", "desc", SORT_FIELDS);

        assertThat(sort.getOrderFor("description").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void shouldReturnBadRequestWhenSortFieldIsUnknown() {
        // camelCase entity names are no longer accepted: only the snake_case API names.
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> SortFieldResolver.resolve("movementDate", "ASC", SORT_FIELDS));

        assertThat(exception.getValidations()).containsKey("sortBy");
        assertThat(exception.getValidations().get("sortBy")).contains("movement_date");
    }

    @Test
    void shouldReturnBadRequestWhenSortFieldIsNull() {
        assertThrows(InvalidFieldsException.class, () -> SortFieldResolver.resolve(null, "ASC", SORT_FIELDS));
    }
}
