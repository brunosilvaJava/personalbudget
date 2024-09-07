package com.bts.personalbudget.exception;

import com.bts.personalbudget.controller.validation.ValidationResponse;
import com.bts.personalbudget.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponse> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new HashMap<>();
        final List<ObjectError> allErrors = ex.getBindingResult().getAllErrors();
        allErrors.forEach((error) -> {
            final FieldError fieldError = (FieldError) error;
            try {
                final Field field = ex.getTarget().getClass().getDeclaredField(fieldError.getField());
                final JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
                if (jsonPropertyAnnotation != null) {
                    errors.put(jsonPropertyAnnotation.value(), fieldError.getDefaultMessage());
                } else {
                    errors.put(fieldError.getField(), error.getDefaultMessage());
                }
            } catch (NoSuchFieldException e) {
                errors.put(fieldError.getField(), error.getDefaultMessage());
            }
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

    @ExceptionHandler(InvalidFieldsException.class)
    public ResponseEntity<ValidationResponse> handleJsonErrors(InvalidFieldsException exception) {
        final Map<String, String> validations = new HashMap<>();
        exception.getValidations().forEach((key, value) -> validations.put(StringUtils.camelToSnake(key), value));
        return new ResponseEntity<>(
                new ValidationResponse(exception.getMessage(), validations), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler({MissingServletRequestParameterException.class})
    public ResponseEntity<ValidationResponse> handlerFolderNotFoundException(MissingServletRequestParameterException exception) {
        return new ResponseEntity<>(
                new ValidationResponse("Parâmetros inválidos",
                        Map.of(exception.getParameterName(), exception.getMessage())),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ValidationResponse> handlerFolderNotFoundException(MethodArgumentTypeMismatchException exception) {
        return new ResponseEntity<>(
                new ValidationResponse("Parâmetros inválidos",
                        Map.of(exception.getName(), exception.getMessage())),
                HttpStatus.BAD_REQUEST);
    }

}
