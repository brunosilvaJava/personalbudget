package com.bts.personalbudget.controller.thirdparty;

import jakarta.validation.constraints.Size;

public record ThirdPartyUpdateRequest(@Size(min = 1, max = 80) String name) { }
