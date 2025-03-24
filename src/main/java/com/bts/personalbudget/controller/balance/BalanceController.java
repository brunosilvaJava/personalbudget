package com.bts.personalbudget.controller.balance;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.mapper.BalanceMapper;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/balance")
@RestController
public class BalanceController {

    private final BalanceService balanceService;
    private final BalanceMapper mapper;

    @GetMapping("/daily")
    public Set<DailyBalanceResponse> balance(@RequestParam LocalDate initialDate,
                                             @RequestParam LocalDate endDate) {
        log.info("m=balance, initialDate={}, endDate={}", initialDate, endDate);
        return mapper.toResponses(balanceService.findDailyBalanceBetween(initialDate, endDate));
    }

}
