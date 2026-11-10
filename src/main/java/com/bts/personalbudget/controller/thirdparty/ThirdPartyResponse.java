package com.bts.personalbudget.controller.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record ThirdPartyResponse(UUID code, String name, ThirdPartyStatus status,
                                 @JsonProperty("open_amount") BigDecimal openAmount,
                                 @JsonProperty("late_amount") BigDecimal lateAmount,
                                 @JsonProperty("open_count") long openCount) { }
