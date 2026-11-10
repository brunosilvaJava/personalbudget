package com.bts.personalbudget.controller.thirdparty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThirdPartyRequest(@NotBlank @Size(max = 80) String name) { }
