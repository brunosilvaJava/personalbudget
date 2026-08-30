package com.bts.personalbudget.exception;

import com.bts.personalbudget.controller.validation.ValidationResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    @DisplayName("Deve retornar 500 com mensagem genérica para exceção não mapeada")
    void shouldHandleGenericException() {
        final RuntimeException exception = new RuntimeException("erro interno inesperado, com dados sensíveis");

        final ResponseEntity<ErrorResponse> response = handler.handleGenericException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Erro interno no servidor");
        assertThat(response.getBody().message()).doesNotContain("dados sensíveis");
    }

    @Test
    @DisplayName("Handler específico de InvalidFieldsException deve continuar funcionando sem regressão")
    void shouldStillHandleInvalidFieldsExceptionSpecifically() {
        final InvalidFieldsException exception = new InvalidFieldsException(
                "Campos inválidos", String.class, Map.of("campo", "mensagem"));

        final ResponseEntity<ValidationResponse> response = handler.handleJsonErrors(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().validations()).containsEntry("campo", "mensagem");
    }

    @Test
    @DisplayName("Handler específico de MissingServletRequestParameterException deve continuar funcionando sem regressão")
    void shouldStillHandleMissingParameterExceptionSpecifically() {
        final MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("param", "String");

        final ResponseEntity<ValidationResponse> response = handler.handlerFolderNotFoundException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().validations()).containsKey("param");
    }

}
