package com.bts.personalbudget.controller.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ReceivableStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableResponse(UUID code, @JsonProperty("third_party_code") UUID thirdPartyCode,
                                 ReceivableStatus status, BigDecimal amount,
                                 @JsonProperty("received_amount") BigDecimal receivedAmount,
                                 @JsonProperty("written_off_amount") BigDecimal writtenOffAmount,
                                 @JsonProperty("remaining_amount") BigDecimal remainingAmount,
                                 @JsonProperty("due_date") LocalDate dueDate,
                                 @JsonProperty("purchase_code") UUID purchaseCode,
                                 @JsonProperty("installment_number") Integer installmentNumber,
                                 @JsonProperty("invoice_code") UUID invoiceCode, String description) { }
