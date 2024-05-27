package com.bts.personalbudget.core.domain.repository;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString
@Getter
@RequiredArgsConstructor
public class InvalidFieldsException extends RuntimeException {
    private final String message;
    private final Class aClass;
    private final Map<String, String> validations;

}
