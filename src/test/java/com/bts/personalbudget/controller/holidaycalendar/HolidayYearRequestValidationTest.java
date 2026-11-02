package com.bts.personalbudget.controller.holidaycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class HolidayYearRequestValidationTest {

    @Test
    void shouldRejectNullHolidayElements() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            final var violations = validatorFactory.getValidator()
                    .validate(new HolidayYearRequest(Collections.singletonList(null)));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).contains("holidays");
        }
    }
}
