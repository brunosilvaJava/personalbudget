package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.creditcard.CreditCardLimitResponse;
import com.bts.personalbudget.controller.creditcard.CreditCardRequest;
import com.bts.personalbudget.controller.creditcard.CreditCardResponse;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardLimit;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CreditCardMapper {

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "status", ignore = true)
    CreditCard toModel(CreditCardRequest request);

    @Mapping(target = "defaultAccountCode", source = "defaultAccount.code")
    @Mapping(target = "defaultThirdPartyCode", source = "defaultThirdParty.code")
    CreditCard toModel(CreditCardEntity entity);

    List<CreditCard> toModelList(List<CreditCardEntity> entities);

    /**
     * Status is not taken from the model: a new card is ACTIVE. The default account comes from a
     * lookup by code that the service owns.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "defaultAccount", ignore = true)
    @Mapping(target = "defaultThirdParty", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    CreditCardEntity toEntity(CreditCard card);

    CreditCardResponse toResponse(CreditCard card);

    List<CreditCardResponse> toResponseList(List<CreditCard> cards);

    CreditCardLimitResponse toResponse(CreditCardLimit limit);
}
