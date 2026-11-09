package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class AccountTransferFactory {

    public static final UUID TRANSFER_CODE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 11, 5);

    private AccountTransferFactory() {
    }

    /** What a client sends: 200.00 from the main account to the wallet, no code. */
    public static AccountTransfer buildNewModel() {
        return new AccountTransfer(null, AccountFactory.ACCOUNT_CODE, AccountFactory.OTHER_ACCOUNT_CODE,
                new BigDecimal("200.00"), EFFECTIVE_DATE, "Reserva", null, null);
    }

    public static AccountTransfer buildModel() {
        return new AccountTransfer(TRANSFER_CODE, AccountFactory.ACCOUNT_CODE, AccountFactory.OTHER_ACCOUNT_CODE,
                new BigDecimal("200.00"), EFFECTIVE_DATE, "Reserva", null, 0L);
    }

    public static AccountTransferEntity buildEntity(final AccountEntity from, final AccountEntity to) {
        return AccountTransferEntity.builder()
                .id(10L)
                .code(TRANSFER_CODE)
                .fromAccount(from)
                .toAccount(to)
                .amount(new BigDecimal("200.00"))
                .effectiveDate(EFFECTIVE_DATE)
                .description("Reserva")
                .version(0L)
                .build();
    }
}
