package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.model.CreditCard;
import java.math.BigDecimal;
import java.util.UUID;

public final class CreditCardFactory {

    public static final UUID CARD_CODE = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    public static final String CARD_NAME = "Nubank Roxinho";

    private CreditCardFactory() {
    }

    /** An ACTIVE card closing on the 25th and due on the 5th, limit 5000.00, no default account. */
    public static CreditCardEntity buildEntity() {
        return CreditCardEntity.builder()
                .id(1L)
                .code(CARD_CODE)
                .name(CARD_NAME)
                .closingDay(25)
                .dueDay(5)
                .creditLimit(new BigDecimal("5000.00"))
                .status(CreditCardStatus.ACTIVE)
                .version(0L)
                .build();
    }

    public static CreditCard buildModel() {
        return new CreditCard(CARD_CODE, CARD_NAME, 25, 5, new BigDecimal("5000.00"), null, CreditCardStatus.ACTIVE);
    }

    /** What a client sends on creation: no code and no status. */
    public static CreditCard buildNewModel(final String name) {
        return new CreditCard(null, name, 25, 5, new BigDecimal("5000.00"), null, null);
    }
}
