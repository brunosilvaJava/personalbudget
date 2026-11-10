package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ThirdParty(UUID code, String name, ThirdPartyStatus status, BigDecimal openAmount,
                         BigDecimal lateAmount, long openCount) {
    public ThirdParty(final UUID code, final String name, final ThirdPartyStatus status) {
        this(code, name, status, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }
}
