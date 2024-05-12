package com.bts.personalbudget.exception;

import com.bts.personalbudget.controller.validation.ValidationResponse;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerExceptionHandler {

    private static final Pattern ENUM_MSG = Pattern.compile("values accepted for Enum class: \\[([^\\]])\\]");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponse> resourceNotFoundException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(new ValidationResponse("Campos inválidos", errors), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationResponse> handleJsonErrors(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof InvalidFormatException invalidFormatException) {
            final Class<?> targetType = invalidFormatException.getTargetType();
            if (targetType.isEnum()) {
                return buildEnumFieldValidationResponse(invalidFormatException, targetType);
            }
        }
        throw exception;
    }

    private static ResponseEntity<ValidationResponse> buildEnumFieldValidationResponse(
            InvalidFormatException invalidFormatException,
            Class<?> targetType) {
        final Optional<String> fieldOptional = invalidFormatException.getPath().stream().map(Reference::getFieldName).findFirst();
        return new ResponseEntity<>(
                new ValidationResponse(
                        "Campos inválidos",
                        Map.of(fieldOptional.orElse(targetType.getSimpleName()),
                                "valores permitidos: " +
                                        Arrays.asList(targetType.getEnumConstants()))),
                HttpStatus.BAD_REQUEST);
    }

}
