package com.bts.personalbudget.controller.validation;

import java.util.Map;

public record ValidationResponse(
        String message,
        Map<String, String> validations
) {
}
