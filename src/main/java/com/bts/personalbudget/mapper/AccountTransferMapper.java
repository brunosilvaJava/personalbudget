package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.accounttransfer.AccountTransferRequest;
import com.bts.personalbudget.controller.accounttransfer.AccountTransferResponse;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransfer;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountTransferMapper {

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "reversedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    AccountTransfer toModel(AccountTransferRequest request);

    @Mapping(target = "fromAccountCode", source = "fromAccount.code")
    @Mapping(target = "toAccountCode", source = "toAccount.code")
    AccountTransfer toModel(AccountTransferEntity entity);

    List<AccountTransfer> toModelList(List<AccountTransferEntity> entities);

    /** The accounts come from a lookup by code that the service owns. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "reversedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "fromAccount", ignore = true)
    @Mapping(target = "toAccount", ignore = true)
    AccountTransferEntity toEntity(AccountTransfer model);

    AccountTransferResponse toResponse(AccountTransfer model);

    List<AccountTransferResponse> toResponseList(List<AccountTransfer> models);
}
