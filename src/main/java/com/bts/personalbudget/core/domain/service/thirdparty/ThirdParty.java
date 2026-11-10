package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import java.util.UUID;

public record ThirdParty(UUID code, String name, ThirdPartyStatus status) { }
