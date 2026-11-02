package com.bts.personalbudget.controller.balance;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.mapper.BalanceMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BalanceService balanceService;

    @MockBean
    private BalanceMapper balanceMapper;

    // PersonalBudgetApplication has @EnableJpaAuditing, which needs a JPA mapping context the
    // web slice does not create.
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void shouldAcceptSnakeCaseDateParamsOnDailyBalance() throws Exception {
        mockMvc.perform(get("/balance/daily")
                        .param("initial_date", "2026-11-01")
                        .param("end_date", "2026-11-30"))
                .andExpect(status().isOk());

        verify(balanceService).findDailyBalanceBetween(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    @Test
    void shouldReturnBadRequestWhenDailyBalanceDatesAreCamelCase() throws Exception {
        mockMvc.perform(get("/balance/daily")
                        .param("initialDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isBadRequest());
    }
}
