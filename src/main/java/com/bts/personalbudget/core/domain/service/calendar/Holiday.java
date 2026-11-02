package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.exception.InvalidFieldsException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * A concrete holiday date with a name, so a yearly review can tell what each date is. Not a rule
 * ("every Sep 7"): the calendar holds the dates of the years that were reviewed.
 */
public record Holiday(LocalDate date, String name) {

    public Holiday {
        final Map<String, String> errors = new HashMap<>();
        if (date == null) {
            errors.put("date", "deve ser informada");
        }
        if (name == null || name.isBlank()) {
            errors.put("name", "deve ser informado");
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException("Campos inválidos", Holiday.class, errors);
        }
    }
}
