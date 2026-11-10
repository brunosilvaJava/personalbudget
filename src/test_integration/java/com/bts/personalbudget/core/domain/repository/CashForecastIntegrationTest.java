package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.core.domain.service.forecast.CashForecast;
import com.bts.personalbudget.core.domain.service.forecast.CashForecastService;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the forecast before and after each change, so data from other tests cancels out. The
 * dates are relative to the real clock.
 */
@Testcontainers
@SpringBootTest
class CashForecastIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    private static final UUID OBLIGATION = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f1");
    private static final UUID LATE_INCOME = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f1");

    @Autowired
    private CashForecastService forecastService;

    @Autowired
    private CreditCardService cardService;

    @Autowired
    private CardPurchaseService purchaseService;

    @Autowired
    private FinancialMovementRepository movementRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID cardCode;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM financial_movement WHERE code IN (UUID_TO_BIN(?), UUID_TO_BIN(?))",
                OBLIGATION.toString(), LATE_INCOME.toString());
        if (cardCode != null) {
            final String text = cardCode.toString();
            jdbc.update("DELETE fm FROM financial_movement fm JOIN card_purchase p ON p.id = fm.card_purchase_id "
                    + "JOIN credit_card c ON c.id = p.credit_card_id WHERE c.code = UUID_TO_BIN(?)", text);
            jdbc.update("DELETE FROM card_purchase WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM card_invoice WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card_plastic WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card_holder WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card WHERE code = UUID_TO_BIN(?)", text);
        }
    }

    private FinancialMovementEntity movement(final UUID code, final OperationType type, final String amount,
                                             final FinancialMovementStatus status, final LocalDate due) {
        final LocalDateTime dueDate = due.atStartOfDay();
        return FinancialMovementEntity.builder().code(code).flagActive(true).operationType(type)
                .description("Forecast test").amount(new BigDecimal(amount)).amountPaid(BigDecimal.ZERO)
                .movementDate(dueDate).dueDate(dueDate).status(status).competenceDate(due)
                .competenceOverridden(true).build();
    }

    @Test
    void shouldMoveAnObligationIntoTheObligationsAndOutOfTheProjectedCash() {
        final LocalDate until = LocalDate.now().plusDays(30);
        final CashForecast before = forecastService.forecast(until, false);

        movementRepository.saveAndFlush(movement(OBLIGATION, OperationType.DEBIT, "-70.00",
                FinancialMovementStatus.PENDING, LocalDate.now().plusDays(10)));
        final CashForecast after = forecastService.forecast(until, false);

        assertThat(after.obligations().subtract(before.obligations())).isEqualByComparingTo("70.00");
        assertThat(after.cardInvoices()).isEqualByComparingTo(before.cardInvoices());
        assertThat(before.projectedCash().subtract(after.projectedCash())).isEqualByComparingTo("70.00");
        assertThat(before.cashBalance()).isEqualByComparingTo(after.cashBalance());
    }

    @Test
    void shouldKeepLateIncomeOutOfTheProjectedCashUnlessIncluded() {
        final LocalDate until = LocalDate.now().plusDays(30);
        final CashForecast before = forecastService.forecast(until, false);
        final CashForecast beforeIncluded = forecastService.forecast(until, true);

        movementRepository.saveAndFlush(movement(LATE_INCOME, OperationType.CREDIT, "40.00",
                FinancialMovementStatus.LATE, LocalDate.now().minusDays(3)));
        final CashForecast excluded = forecastService.forecast(until, false);
        final CashForecast included = forecastService.forecast(until, true);

        assertThat(excluded.lateIncome().subtract(before.lateIncome())).isEqualByComparingTo("40.00");
        assertThat(excluded.projectedCash()).isEqualByComparingTo(before.projectedCash());
        assertThat(included.projectedCash().subtract(beforeIncluded.projectedCash())).isEqualByComparingTo("40.00");
        assertThat(excluded.lowestProjectedCash()).isEqualByComparingTo(before.lowestProjectedCash());
    }

    @Test
    void shouldSplitCardInstallmentsIntoInvoicesDueByTheHorizonAndCardStillCommittedAfterIt() {
        final CreditCard card = cardService.create(new CreditCard(null, "Cartão " + UUID.randomUUID(), 25, 5,
                new BigDecimal("1000.00"), null, null));
        cardCode = card.code();
        final CardPurchase purchase = purchaseService.create(new CardPurchase(null, card.code(), "Compra",
                LocalDate.now(), 6, new BigDecimal("10.00"), null, null, null));
        final LocalDate firstDue = purchase.installmentList().get(0).dueDate();
        final LocalDate until = firstDue.isBefore(LocalDate.now()) ? LocalDate.now() : firstDue;

        final CashForecast forecast = forecastService.forecast(until, false);

        assertThat(purchase.installmentList()).hasSize(6);
        assertThat(forecast.cardInvoices()).isGreaterThanOrEqualTo(new BigDecimal("10.00"));
        assertThat(forecast.cardBeyondHorizon()).isGreaterThanOrEqualTo(new BigDecimal("50.00"));
        // never in the obligations: the same purchase counted in the invoices only
        final CashForecast withoutCard = forecastService.forecast(until, false);
        assertThat(withoutCard.obligations()).isEqualByComparingTo(forecast.obligations());
    }
}
