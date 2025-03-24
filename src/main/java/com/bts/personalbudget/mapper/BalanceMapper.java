package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.balance.DailyBalanceResponse;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BalanceMapper {

    @Mapping(target = "balance.opening", source = "openingBalance")
    @Mapping(target = "balance.totalRevenue", source = "totalRevenue")
    @Mapping(target = "balance.totalExpense", source = "totalExpense")
    @Mapping(target = "balance.closing", source = "closingBalance")
    @Mapping(target = "projected.opening", source = "projectedOpeningBalance")
    @Mapping(target = "projected.pendingTotalRevenue", source = "pendingTotalRevenue")
    @Mapping(target = "projected.pendingTotalExpense", source = "pendingTotalExpense")
    @Mapping(target = "projected.closing", source = "projectedClosingBalance")
    DailyBalanceResponse toResponse(DailyBalance dailyBalance);
    Set<DailyBalanceResponse> toResponses(Set<DailyBalance> dailyBalanceSet);

}
