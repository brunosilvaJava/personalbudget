package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.exception.InvalidFieldsException;
import java.util.Map;
import java.util.UUID;

/** Pure precedence rule shared by an individual purchase and a batch-import item. */
public final class ThirdPartyResolver {
    private ThirdPartyResolver() { }

    public static UUID resolve(final UUID explicitCode, final Boolean ownExpense,
                               final UUID plasticDefault, final UUID cardDefault) {
        if (Boolean.TRUE.equals(ownExpense) && explicitCode != null) {
            throw new InvalidFieldsException("Campos inválidos", ThirdPartyResolver.class,
                    Map.of("thirdPartyCode", "não pode ser informado quando ownExpense for true"));
        }
        if (Boolean.TRUE.equals(ownExpense)) return null;
        return explicitCode != null ? explicitCode : (plasticDefault != null ? plasticDefault : cardDefault);
    }
}
