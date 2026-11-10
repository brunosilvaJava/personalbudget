package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePayment;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePaymentService;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoiceService;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
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

/**
 * The card is counted once in the cash flow: the purchase shows up as a pending expense on the
 * invoice due date, and paying the invoice moves that same amount to the payment date as a real
 * expense. Dates in 2092; the balance is compared before and after, so any other data cancels out.
 */
@Testcontainers
@SpringBootTest
class CardCashFlowIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    private static final LocalDate FROM = LocalDate.of(2092, 4, 1);
    private static final LocalDate TO = LocalDate.of(2092, 4, 10);
    private static final LocalDate DUE_DATE = LocalDate.of(2092, 4, 5);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2092, 4, 2);

    @Autowired
    private CreditCardService cardService;

    @Autowired
    private CardPurchaseService purchaseService;

    @Autowired
    private CardInvoiceService invoiceService;

    @Autowired
    private CardInvoicePaymentService paymentService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private JdbcTemplate jdbc;

    private final ConcurrentLinkedQueue<UUID> cardCodes = new ConcurrentLinkedQueue<>();

    @AfterEach
    void cleanUp() {
        UUID code;
        while ((code = cardCodes.poll()) != null) {
            final String text = code.toString();
            jdbc.update("DELETE ms FROM movement_settlement ms "
                    + "JOIN financial_movement fm ON fm.id = ms.financial_movement_id "
                    + "JOIN card_purchase p ON p.id = fm.card_purchase_id "
                    + "JOIN credit_card c ON c.id = p.credit_card_id WHERE c.code = UUID_TO_BIN(?)", text);
            jdbc.update("DELETE fm FROM financial_movement fm "
                    + "JOIN card_purchase p ON p.id = fm.card_purchase_id "
                    + "JOIN credit_card c ON c.id = p.credit_card_id WHERE c.code = UUID_TO_BIN(?)", text);
            jdbc.update("DELETE FROM card_purchase WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM card_invoice_payment WHERE card_invoice_id IN (SELECT id FROM card_invoice "
                    + "WHERE credit_card_id = (SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?)))", text);
            jdbc.update("DELETE FROM card_invoice WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card_plastic WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card_holder WHERE credit_card_id = "
                    + "(SELECT id FROM credit_card WHERE code = UUID_TO_BIN(?))", text);
            jdbc.update("DELETE FROM credit_card WHERE code = UUID_TO_BIN(?)", text);
        }
    }

    private BigDecimal realExpense(final LocalDate date) {
        return balanceService.findDailyBalanceBetween(FROM, TO).stream()
                .filter(day -> day.getDate().equals(date)).map(DailyBalance::getTotalExpense)
                .findFirst().orElseThrow();
    }

    private BigDecimal pendingExpense(final LocalDate date) {
        return balanceService.findDailyBalanceBetween(FROM, TO).stream()
                .filter(day -> day.getDate().equals(date)).map(DailyBalance::getPendingTotalExpense)
                .findFirst().orElseThrow();
    }

    @Test
    void shouldCountThePurchaseOnTheDueDateAndMoveItToThePaymentDateWhenTheInvoiceIsPaid() {
        final BigDecimal baselineRealOnDue = realExpense(DUE_DATE);
        final BigDecimal baselineRealOnPayment = realExpense(PAYMENT_DATE);
        final BigDecimal baselinePendingOnDue = pendingExpense(DUE_DATE);
        final CreditCard card = cardService.create(new CreditCard(null, "Cartão " + UUID.randomUUID(), 25, 5,
                new BigDecimal("1000.00"), null, null));
        cardCodes.add(card.code());
        purchaseService.create(new CardPurchase(null, card.code(), "Compra", LocalDate.of(2092, 3, 10), 1,
                new BigDecimal("100.00"), null, null, null));

        // purchased, not paid: a pending expense on the due date and nothing real yet
        assertThat(pendingExpense(DUE_DATE).subtract(baselinePendingOnDue)).isEqualByComparingTo("-100.00");
        assertThat(realExpense(DUE_DATE)).isEqualByComparingTo(baselineRealOnDue);
        assertThat(realExpense(PAYMENT_DATE)).isEqualByComparingTo(baselineRealOnPayment);

        final CardInvoicePayment payment = paymentService.pay(
                invoiceService.find(card.code(), YearMonth.of(2092, 3)).get(0).code(), null, PAYMENT_DATE);

        // paid: the same 100.00 left the pending side and is a real expense on the payment date, once
        assertThat(payment.amount()).isEqualByComparingTo("100.00");
        assertThat(pendingExpense(DUE_DATE)).isEqualByComparingTo(baselinePendingOnDue);
        assertThat(realExpense(DUE_DATE)).isEqualByComparingTo(baselineRealOnDue);
        assertThat(realExpense(PAYMENT_DATE).subtract(baselineRealOnPayment)).isEqualByComparingTo("-100.00");

        paymentService.reverse(invoiceService.find(card.code(), YearMonth.of(2092, 3)).get(0).code(),
                payment.code());

        // reversed: back to a pending expense on the due date, nothing real left
        assertThat(pendingExpense(DUE_DATE).subtract(baselinePendingOnDue)).isEqualByComparingTo("-100.00");
        assertThat(realExpense(PAYMENT_DATE)).isEqualByComparingTo(baselineRealOnPayment);
    }
}
