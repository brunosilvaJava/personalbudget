package com.bts.personalbudget.core.domain.service.thirdparty;

import java.util.UUID;

/** The one category every receivable write-off becomes an expense in; created by the V33 migration. */
public final class ReceivableWriteOffCategory {
    public static final UUID CODE = UUID.fromString("6f1d2c40-8a3e-4b7a-9c55-0d3e7a1b2c33");

    private ReceivableWriteOffCategory() {
    }
}
