package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class CreditCardIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private CreditCardService cardService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    private final ConcurrentLinkedQueue<UUID> cardCodes = new ConcurrentLinkedQueue<>();

    @AfterEach
    void cleanUp() {
        UUID code;
        while ((code = cardCodes.poll()) != null) {
            jdbc.update("DELETE FROM credit_card WHERE code = UUID_TO_BIN(?)", code.toString());
        }
    }

    private CreditCard create(final String name, final BigDecimal limit, final UUID accountCode) {
        final CreditCard card = cardService.create(new CreditCard(null, name, 25, 5, limit, accountCode, null));
        cardCodes.add(card.code());
        return card;
    }

    @Test
    void shouldPersistACardWithLimitAndDefaultAccountAndReadItBack() {
        final Account account = accountService.create(new Account(null, "Conta cartão " + UUID.randomUUID(),
                AccountType.CHECKING, null, LocalDate.of(2093, 1, 1), null, false));

        final CreditCard created = create("Cartão " + UUID.randomUUID(), new BigDecimal("5000.00"), account.code());

        final CreditCard found = cardService.findByCode(created.code());
        assertThat(found.closingDay()).isEqualTo(25);
        assertThat(found.dueDay()).isEqualTo(5);
        assertThat(found.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(found.defaultAccountCode()).isEqualTo(account.code());
        assertThat(found.status()).isEqualTo(CreditCardStatus.ACTIVE);
    }

    @Test
    void shouldHideInactiveCardsFromTheDefaultListingButKeepThemReachable() {
        final CreditCard card = create("Cartão " + UUID.randomUUID(), null, null);

        cardService.inactivate(card.code());

        assertThat(cardService.find(null)).extracting(CreditCard::code).doesNotContain(card.code());
        assertThat(cardService.find(List.of(CreditCardStatus.INACTIVE))).extracting(CreditCard::code)
                .contains(card.code());
        assertThat(cardService.findByCode(card.code()).status()).isEqualTo(CreditCardStatus.INACTIVE);

        cardService.activate(card.code());

        assertThat(cardService.find(null)).extracting(CreditCard::code).contains(card.code());
    }

    @Test
    void shouldUpdateDaysAndLimitAndRejectADuplicateName() {
        final String name = "Cartão " + UUID.randomUUID();
        final CreditCard first = create(name, null, null);
        final CreditCard second = create("Cartão " + UUID.randomUUID(), null, null);

        final CreditCard updated = cardService.update(first.code(), null, 10, 17, new BigDecimal("1200.50"), null);

        assertThat(updated.closingDay()).isEqualTo(10);
        assertThat(updated.dueDay()).isEqualTo(17);
        assertThat(cardService.findByCode(first.code()).creditLimit()).isEqualByComparingTo("1200.50");
        assertThrows(InvalidFieldsException.class,
                () -> cardService.update(second.code(), name, null, null, null, null));
    }

    @Test
    void shouldRefuseAnInactiveDefaultAccount() {
        final Account account = accountService.create(new Account(null, "Conta inativa " + UUID.randomUUID(),
                AccountType.CASH, null, LocalDate.of(2093, 1, 1), null, false));
        accountService.inactivate(account.code());
        assertThat(accountService.findByCode(account.code()).status()).isEqualTo(AccountStatus.INACTIVE);

        assertThrows(InvalidFieldsException.class,
                () -> cardService.create(new CreditCard(null, "Cartão " + UUID.randomUUID(), 25, 5, null,
                        account.code(), null)));
    }
}
