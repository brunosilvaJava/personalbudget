package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.bts.personalbudget.core.domain.model.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class AccountFactory {

    public static final UUID ACCOUNT_CODE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID OTHER_ACCOUNT_CODE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final String ACCOUNT_NAME = "Conta principal";
    public static final LocalDate INITIAL_BALANCE_DATE = LocalDate.of(2026, 1, 1);

    private AccountFactory() {
    }

    /** An ACTIVE default CHECKING account with initial balance 100.00 on 2026-01-01. */
    public static AccountEntity buildEntity() {
        return AccountEntity.builder()
                .id(1L)
                .code(ACCOUNT_CODE)
                .name(ACCOUNT_NAME)
                .type(AccountType.CHECKING)
                .initialBalance(new BigDecimal("100.00"))
                .initialBalanceDate(INITIAL_BALANCE_DATE)
                .status(AccountStatus.ACTIVE)
                .defaultAccount(true)
                .build();
    }

    public static AccountEntity buildEntity(final Long id, final UUID code, final String name,
                                            final boolean defaultAccount) {
        final AccountEntity entity = buildEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setDefaultAccount(defaultAccount);
        return entity;
    }

    public static Account buildModel() {
        return new Account(ACCOUNT_CODE, ACCOUNT_NAME, AccountType.CHECKING, new BigDecimal("100.00"),
                INITIAL_BALANCE_DATE, AccountStatus.ACTIVE, true);
    }

    /** What a client sends on creation: no code, status or default flag. */
    public static Account buildNewModel(final String name) {
        return new Account(null, name, AccountType.CASH, null, null, null, false);
    }
}
