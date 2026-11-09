package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.account.AccountBalanceResponse;
import com.bts.personalbudget.controller.account.AccountsBalanceResponse;
import com.bts.personalbudget.controller.account.AccountRequest;
import com.bts.personalbudget.controller.account.AccountResponse;
import com.bts.personalbudget.controller.account.AccountStatementResponse;
import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.core.domain.service.account.AccountBalance;
import com.bts.personalbudget.core.domain.service.account.AccountStatement;
import com.bts.personalbudget.core.domain.service.account.AccountsBalance;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "defaultAccount", ignore = true)
    Account toModel(AccountRequest request);

    Account toModel(AccountEntity entity);

    List<Account> toModelList(List<AccountEntity> entities);

    /**
     * Status and default flag are not taken from the model: a new account is ACTIVE and never
     * the default one (the default is chosen through an update).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "defaultAccount", constant = "false")
    AccountEntity toEntity(Account account);

    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponseList(List<Account> accounts);

    AccountBalanceResponse toResponse(AccountBalance balance);

    AccountsBalanceResponse toResponse(AccountsBalance balances);

    AccountStatementResponse toResponse(AccountStatement statement);
}
