package com.bts.personalbudget.controller.creditcardholder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Fields to update on a credit-card holder")
public record CreditCardHolderUpdateRequest(@Size(min = 1, max = 80) String name) {
}
