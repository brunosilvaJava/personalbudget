package com.bts.personalbudget.controller.common;

import com.bts.personalbudget.exception.InvalidFieldsException;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.data.domain.Sort;

/**
 * Translates the sort_by/sort_direction query params of the listing endpoints, which the API
 * takes in snake_case, into a Spring Data Sort on the entity property.
 */
public final class SortFieldResolver {

    private SortFieldResolver() {
    }

    /**
     * @param allowedFields each sort_by value the endpoint accepts, mapped to the entity property
     *                      it sorts on. Any other value is rejected with 400 instead of reaching
     *                      Spring Data, where an unknown property fails as a 500.
     */
    public static Sort resolve(final String sortBy,
                               final String sortDirection,
                               final Map<String, String> allowedFields) {
        final String property = sortBy == null ? null : allowedFields.get(sortBy);
        if (property == null) {
            throw new InvalidFieldsException(
                    "Parâmetros inválidos",
                    SortFieldResolver.class,
                    Map.of("sortBy", "valores permitidos: " + new TreeSet<>(allowedFields.keySet())));
        }
        final Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
