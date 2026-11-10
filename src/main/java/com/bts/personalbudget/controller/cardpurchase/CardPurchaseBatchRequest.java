package com.bts.personalbudget.controller.cardpurchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CardPurchaseBatchRequest(@NotNull @JsonProperty("credit_card_code") UUID creditCardCode,
                                       @JsonProperty("dry_run") Boolean dryRun,
                                       @NotNull @Size(min = 1, max = 300) List<@Valid CardPurchaseBatchItemRequest> items) { }
