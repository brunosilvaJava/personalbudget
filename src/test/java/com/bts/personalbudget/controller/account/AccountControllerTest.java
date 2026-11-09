package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.service.account.AccountBalance;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.core.domain.service.account.AccountsBalance;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.ControllerExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import com.bts.personalbudget.mapper.AccountMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @InjectMocks private AccountController controller;
    @Mock private AccountService service;
    @Spy private AccountMapper mapper = Mappers.getMapper(AccountMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void shouldCreateAccountWithCreatedStatusAndSnakeCaseBody() throws Exception {
        when(service.create(any())).thenReturn(AccountFactory.buildModel());

        mvc.perform(post("/account").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nubank\",\"type\":\"CHECKING\",\"initial_balance\":100.00,"
                                + "\"initial_balance_date\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(AccountFactory.ACCOUNT_CODE.toString()))
                .andExpect(jsonPath("$.initial_balance").value(100.00))
                .andExpect(jsonPath("$.initial_balance_date").value("2026-01-01"))
                .andExpect(jsonPath("$.default").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectCreationWithoutNameOrTypeBeforeCallingService() throws Exception {
        mvc.perform(post("/account").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldListAccountsAndFindByCode() throws Exception {
        when(service.find(isNull())).thenReturn(List.of(AccountFactory.buildModel()));
        when(service.findByCode(AccountFactory.ACCOUNT_CODE)).thenReturn(AccountFactory.buildModel());

        mvc.perform(get("/account")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(AccountFactory.ACCOUNT_NAME));
        mvc.perform(get("/account/{code}", AccountFactory.ACCOUNT_CODE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CHECKING"));
    }

    @Test
    void shouldPassTheDefaultFlagOfThePatchBodyToTheService() throws Exception {
        when(service.update(eq(AccountFactory.ACCOUNT_CODE), isNull(), isNull(), isNull(), isNull(), eq(true)))
                .thenReturn(AccountFactory.buildModel());

        mvc.perform(patch("/account/{code}", AccountFactory.ACCOUNT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"default\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswerConflictWhenInactivatingTheDefaultAccount() throws Exception {
        when(service.inactivate(AccountFactory.ACCOUNT_CODE))
                .thenThrow(new ConflictException("A conta padrão não pode ser inativada."));

        mvc.perform(patch("/account/{code}/inactivate", AccountFactory.ACCOUNT_CODE))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnTheBalanceOnTheRequestedDate() throws Exception {
        when(service.balance(AccountFactory.ACCOUNT_CODE, LocalDate.of(2026, 11, 5)))
                .thenReturn(new AccountBalance(AccountFactory.ACCOUNT_CODE, LocalDate.of(2026, 11, 5),
                        new BigDecimal("100.00"), new BigDecimal("-30.00"), new BigDecimal("20.00"),
                        new BigDecimal("90.00")));

        mvc.perform(get("/account/{code}/balance", AccountFactory.ACCOUNT_CODE).param("date", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_code").value(AccountFactory.ACCOUNT_CODE.toString()))
                .andExpect(jsonPath("$.initial_balance").value(100.00))
                .andExpect(jsonPath("$.settlements_total").value(-30.00))
                .andExpect(jsonPath("$.transfers_total").value(20.00))
                .andExpect(jsonPath("$.balance").value(90.00));
    }

    @Test
    void shouldReturnTheBalanceOfAllAccountsWithTheTotal() throws Exception {
        final LocalDate date = LocalDate.of(2026, 11, 5);
        when(service.balances(date)).thenReturn(new AccountsBalance(date,
                List.of(new AccountBalance(AccountFactory.ACCOUNT_CODE, date, new BigDecimal("100.00"),
                        BigDecimal.ZERO, new BigDecimal("-20.00"), new BigDecimal("80.00")),
                        new AccountBalance(AccountFactory.OTHER_ACCOUNT_CODE, date, BigDecimal.ZERO,
                                BigDecimal.ZERO, new BigDecimal("20.00"), new BigDecimal("20.00"))),
                new BigDecimal("100.00")));

        mvc.perform(get("/account/balance").param("date", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-05"))
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[1].transfers_total").value(20.00))
                .andExpect(jsonPath("$.total").value(100.00));
    }
}
