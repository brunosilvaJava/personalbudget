package com.bts.personalbudget.exception;

import java.util.Map;

public record ValidationResponse(
        String message,
        Map<String, String> validations
) {
}
