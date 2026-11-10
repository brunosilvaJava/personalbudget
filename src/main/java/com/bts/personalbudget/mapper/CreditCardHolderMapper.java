package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.creditcardholder.CreditCardHolderResponse;
import com.bts.personalbudget.controller.creditcardholder.CreditCardPlasticResponse;
import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardPlasticEntity;
import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardHolder;
import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardPlastic;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CreditCardHolderMapper {
    public CreditCardHolder toModel(final CreditCardHolderEntity entity) {
        return new CreditCardHolder(entity.getCode(), entity.getCard().getCode(), entity.getName(),
                entity.getKind(), entity.getStatus());
    }

    public List<CreditCardHolder> toHolderModelList(final List<CreditCardHolderEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    public CreditCardPlastic toModel(final CreditCardPlasticEntity entity) {
        return new CreditCardPlastic(entity.getCode(), entity.getCard().getCode(), entity.getHolder().getCode(),
                entity.getHolder().getName(), entity.getLastDigits(), entity.getKind(), entity.getStatus(),
                entity.getDefaultThirdParty() == null ? null : entity.getDefaultThirdParty().getCode());
    }

    public List<CreditCardPlastic> toPlasticModelList(final List<CreditCardPlasticEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    public CreditCardHolderResponse toResponse(final CreditCardHolder holder) {
        return new CreditCardHolderResponse(holder.code(), holder.cardCode(), holder.name(), holder.kind(), holder.status());
    }

    public List<CreditCardHolderResponse> toHolderResponseList(final List<CreditCardHolder> holders) {
        return holders.stream().map(this::toResponse).toList();
    }

    public CreditCardPlasticResponse toResponse(final CreditCardPlastic plastic) {
        return new CreditCardPlasticResponse(plastic.code(), plastic.cardCode(), plastic.holderCode(),
                plastic.holderName(), plastic.lastDigits(), plastic.kind(), plastic.status(), plastic.defaultThirdPartyCode());
    }

    public List<CreditCardPlasticResponse> toPlasticResponseList(final List<CreditCardPlastic> plastics) {
        return plastics.stream().map(this::toResponse).toList();
    }
}
