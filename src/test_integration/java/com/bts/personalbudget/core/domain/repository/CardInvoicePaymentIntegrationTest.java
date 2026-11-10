package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoice;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePayment;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePaymentService;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoiceService;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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

/**
 * Card closing on the 25th and due on the 5th, dates in 2093 (no other test uses that year). A
 * purchase on 2093-03-10 puts its installments in the invoices of 2093-03, 2093-04 and 2093-05.
 */
@Testcontainers
@SpringBootTest
class CardInvoicePaymentIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2093, 3, 10);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2093, 4, 5);

    @Autowired
    private CreditCardService cardService;

    @Autowired
    private CardPurchaseService purchaseService;

    @Autowired
    private CardInvoiceService invoiceService;

    @Autowired
    private CardInvoicePaymentService paymentService;

    @Autowired
    private FinancialMovementService movementService;

    @Autowired
    private MovementSettlementService settlementService;

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

    private CreditCard createCard(final String limit) {
        final CreditCard card = cardService.create(new CreditCard(null, "Cartão " + UUID.randomUUID(), 25, 5,
                new BigDecimal(limit), null, null));
        cardCodes.add(card.code());
        return card;
    }

    private CardPurchase purchase(final CreditCard card, final int installments, final String amount) {
        return purchaseService.create(new CardPurchase(null, card.code(), "Compra " + installments + "x",
                PURCHASE_DATE, installments, new BigDecimal(amount), null, null, null));
    }

    private CardInvoice invoice(final CreditCard card, final int month) {
        return invoiceService.find(card.code(), YearMonth.of(2093, month)).get(0);
    }

    private FinancialMovement installment(final CardPurchase purchase, final int index) {
        return movementService.find(purchase.installmentList().get(index).movementCode());
    }

    @Test
    void shouldPayTheInvoiceSettlingOnlyItsInstallmentsAndReleaseTheLimit() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase purchase = purchase(card, 3, "100.00");
        final CardInvoice march = invoice(card, 3);

        final CardInvoicePayment payment = paymentService.pay(march.code(), null, PAYMENT_DATE);

        assertThat(payment.amount()).isEqualByComparingTo("100.00");
        assertThat(payment.reversedAt()).isNull();
        assertThat(installment(purchase, 0).status()).isEqualTo(FinancialMovementStatus.PAID_OUT);
        assertThat(installment(purchase, 1).status()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(installment(purchase, 2).status()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(invoice(card, 3).state()).isEqualTo(CardInvoiceState.PAID);
        assertThat(invoice(card, 4).state()).isEqualTo(CardInvoiceState.OPEN);
        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("200.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement_settlement ms "
                + "JOIN card_invoice_payment p ON p.id = ms.invoice_payment_id WHERE p.code = UUID_TO_BIN(?)",
                Integer.class, payment.code().toString())).isEqualTo(1);
    }

    @Test
    void shouldRefusePayingTwiceAndPayingOrReversingAnInstallmentAlone() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase purchase = purchase(card, 2, "100.00");
        final CardInvoice march = invoice(card, 3);
        final UUID firstInstallment = purchase.installmentList().get(0).movementCode();
        final UUID secondInstallment = purchase.installmentList().get(1).movementCode();

        assertThrows(ConflictException.class, () -> settlementService.create(secondInstallment,
                new MovementSettlement(null, null, new BigDecimal("10.00"), PAYMENT_DATE, null, null, null, null,
                        null, null)));
        final CardInvoicePayment payment = paymentService.pay(march.code(), null, PAYMENT_DATE);

        assertThrows(ConflictException.class, () -> paymentService.pay(march.code(), null, PAYMENT_DATE));
        final UUID settlementCode = settlementService.findAll(firstInstallment).get(0).code();
        assertThrows(ConflictException.class, () -> settlementService.reverse(firstInstallment, settlementCode));
        assertThat(paymentService.findAll(march.code())).extracting(CardInvoicePayment::code)
                .containsExactly(payment.code());
    }

    @Test
    void shouldReverseThePaymentRestoringInstallmentsAndLimitAndAllowPayingAgain() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase purchase = purchase(card, 3, "100.00");
        final CardInvoice march = invoice(card, 3);
        final CardInvoicePayment payment = paymentService.pay(march.code(), null, PAYMENT_DATE);

        final CardInvoicePayment reversed = paymentService.reverse(march.code(), payment.code());

        assertThat(reversed.reversedAt()).isNotNull();
        assertThat(installment(purchase, 0).status()).isIn(FinancialMovementStatus.PENDING,
                FinancialMovementStatus.LATE);
        assertThat(installment(purchase, 0).amountPaid()).isEqualByComparingTo("0");
        assertThat(invoice(card, 3).state()).isNotEqualTo(CardInvoiceState.PAID);
        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("300.00");
        assertThrows(ConflictException.class, () -> paymentService.reverse(march.code(), payment.code()));

        final CardInvoicePayment again = paymentService.pay(march.code(), null, PAYMENT_DATE.plusDays(1));

        assertThat(again.code()).isNotEqualTo(payment.code());
        final List<CardInvoicePayment> history = paymentService.findAll(march.code());
        assertThat(history).extracting(CardInvoicePayment::code).containsExactly(again.code(), payment.code());
        assertThat(history.get(1).reversedAt()).isNotNull();
        assertThat(history.get(0).reversedAt()).isNull();
    }

    @Test
    void shouldPayOnlyTheRemainingOfAnInstallmentThatHadAPaymentBeforeThisFeature() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase purchase = purchase(card, 1, "100.00");
        final UUID movementCode = purchase.installmentList().get(0).movementCode();
        // legacy partial payment recorded straight in the database, as E9b allowed it
        jdbc.update("INSERT INTO movement_settlement (code, financial_movement_id, account_id, amount, "
                + "effective_date, version) SELECT UUID_TO_BIN(UUID()), fm.id, a.id, 60.00, ?, 0 "
                + "FROM financial_movement fm, account a WHERE fm.code = UUID_TO_BIN(?) AND a.is_default = 1",
                PAYMENT_DATE, movementCode.toString());
        jdbc.update("UPDATE financial_movement SET amount_paid = -60.00 WHERE code = UUID_TO_BIN(?)",
                movementCode.toString());

        final CardInvoicePayment payment = paymentService.pay(invoice(card, 3).code(), null, PAYMENT_DATE);

        assertThat(payment.amount()).isEqualByComparingTo("40.00");
        assertThat(movementService.find(movementCode).status()).isEqualTo(FinancialMovementStatus.PAID_OUT);
        paymentService.reverse(invoice(card, 3).code(), payment.code());
        // a DEBIT keeps its paid amount negative, as the movement amount
        assertThat(movementService.find(movementCode).amountPaid()).isEqualByComparingTo("-60.00");
    }
}
