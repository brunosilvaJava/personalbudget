package com.bts.personalbudget.controller.creditcardholder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "An additional holder of a credit card")
public record CreditCardHolderRequest(@NotBlank @Size(max = 80) String name) {
}
