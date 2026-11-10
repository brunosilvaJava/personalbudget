package com.bts.personalbudget.controller.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import java.util.UUID;

public record ThirdPartyResponse(UUID code, String name, ThirdPartyStatus status) { }
