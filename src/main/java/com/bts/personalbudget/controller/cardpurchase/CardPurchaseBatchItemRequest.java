package com.bts.personalbudget.controller.cardpurchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CardPurchaseBatchItemRequest(@NotBlank @Size(max = 100) String description,
                                           @NotNull @JsonProperty("purchase_date") LocalDate purchaseDate,
                                           @NotNull @Min(1) @Max(60) Integer installments,
                                           @JsonProperty("first_installment") @Min(1) @Max(60) Integer firstInstallment,
                                           @NotNull @JsonProperty("installment_amount") @DecimalMin(value = "0.00", inclusive = false)
                                           @Digits(integer = 8, fraction = 2) BigDecimal installmentAmount,
                                           @JsonProperty("category_code") UUID categoryCode,
                                           @JsonProperty("card_last_digits") @Pattern(regexp = "\\d{4}") String cardLastDigits,
                                           @JsonProperty("card_holder_code") UUID cardHolderCode,
                                           @JsonProperty("third_party_code") UUID thirdPartyCode,
                                           @JsonProperty("own_expense") Boolean ownExpense,
                                           @JsonProperty("accept_duplicate") Boolean acceptDuplicate) { }
