package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreditCardPlasticRequest(
        @JsonProperty("holder_code") @NotNull UUID holderCode,
        @JsonProperty("last_digits") @NotBlank @Pattern(regexp = "\\d{4}") String lastDigits,
        @NotNull CreditCardPlasticKind kind) {
}
