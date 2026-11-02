package com.bts.personalbudget.core.domain.model;

import java.util.UUID;

public record CategoryGroup(
        UUID code,
        String name,
        Integer displayOrder
) {
}
