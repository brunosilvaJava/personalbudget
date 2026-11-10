package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoice;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePaymentService;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoiceService;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardLimit;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Card closing on the 25th and due on the 5th. Dates are in 2094, far from every other test: a
 * purchase on 2094-03-10 falls in the invoice of 2094-03 (closes 2094-03-25, due 2094-04-05), and
 * three installments are due on 2094-04-05, 2094-05-05 and 2094-06-05.
 */
@Testcontainers
@SpringBootTest
class CardPurchaseIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2094, 3, 10);

    @Autowired
    private CreditCardService cardService;

    @Autowired
    private CardPurchaseService purchaseService;

    @Autowired
    private CardInvoiceService invoiceService;

    @Autowired
    private FinancialMovementService movementService;

    @Autowired
    private CardInvoicePaymentService paymentService;

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
                limit == null ? null : new BigDecimal(limit), null, null));
        cardCodes.add(card.code());
        return card;
    }

    private CardPurchase purchase(final CreditCard card, final LocalDate date, final int installments,
                                  final String amount) {
        return purchaseService.create(new CardPurchase(null, card.code(), "Compra " + installments + "x", date,
                installments, new BigDecimal(amount), null, null, null));
    }

    @Test
    void shouldCreateInstallmentsAsMovementsWithCompetenceOnTheInvoiceDueDateAndUseTheLimit() {
        final CreditCard card = createCard("1000.00");

        final CardPurchase created = purchase(card, PURCHASE_DATE, 3, "100.00");

        assertThat(created.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(created.installmentList()).extracting(CardInstallment::competenceDate)
                .containsExactly(LocalDate.of(2094, 4, 5), LocalDate.of(2094, 5, 5), LocalDate.of(2094, 6, 5));
        assertThat(created.installmentList()).extracting(CardInstallment::invoiceReferenceMonth)
                .containsExactly(YearMonth.of(2094, 3), YearMonth.of(2094, 4), YearMonth.of(2094, 5));

        final FinancialMovement movement = movementService.find(created.installmentList().get(1).movementCode());
        assertThat(movement.amount()).isEqualByComparingTo("-100.00");
        assertThat(movement.status()).isEqualTo(FinancialMovementStatus.PENDING);
        assertThat(movement.competenceDate()).isEqualTo(LocalDate.of(2094, 5, 5));
        assertThat(movement.dueDate().toLocalDate()).isEqualTo(LocalDate.of(2094, 5, 5));
        assertThat(movement.movementDate().toLocalDate()).isEqualTo(PURCHASE_DATE);
        assertThat(movement.competenceOverridden()).isTrue();
        assertThat(movement.cardPurchaseCode()).isEqualTo(created.code());
        assertThat(movement.cardInvoiceCode()).isEqualTo(created.installmentList().get(1).invoiceCode());
        assertThat(movement.installmentNumber()).isEqualTo(2);

        final CreditCardLimit limit = cardService.limit(card.code());
        assertThat(limit.used()).isEqualByComparingTo("300.00");
        assertThat(limit.available()).isEqualByComparingTo("700.00");
    }

    @Test
    void shouldRefuseAPurchaseAboveTheLimitWithoutSavingAnythingAndAcceptOneExactlyOnIt() {
        final CreditCard card = createCard("1000.00");
        purchase(card, PURCHASE_DATE, 3, "100.00");

        assertThrows(ConflictException.class, () -> purchase(card, PURCHASE_DATE, 8, "100.00"));
        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("300.00");
        assertThat(purchaseService.find(PURCHASE_DATE, PURCHASE_DATE, card.code())).hasSize(1);

        purchase(card, PURCHASE_DATE, 7, "100.00");

        assertThat(cardService.limit(card.code()).available()).isEqualByComparingTo("0");
    }

    @Test
    void shouldNotCheckTheLimitOfACardWithoutLimit() {
        final CreditCard card = createCard(null);

        purchase(card, PURCHASE_DATE, 60, "9999999.99");

        assertThat(cardService.limit(card.code()).available()).isNull();
    }

    @Test
    void shouldNotLetTwoSimultaneousPurchasesPassTheLimit() throws Exception {
        final CreditCard card = createCard("1000.00");
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final Callable<Boolean> buy = () -> {
                    start.await();
                    try {
                        purchase(card, PURCHASE_DATE, 6, "100.00");
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                };
                results.add(pool.submit(buy));
            }
            start.countDown();
            int accepted = 0;
            for (final Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("600.00");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldRecalculateTheInstallmentsOnUpdateAndReleaseTheLimitOnDelete() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase created = purchase(card, PURCHASE_DATE, 3, "100.00");

        final CardPurchase shrunk = purchaseService.update(created.code(), null, null, 2, new BigDecimal("150.00"), null);

        assertThat(shrunk.installmentList()).hasSize(2);
        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("300.00");
        assertThat(purchaseService.findByCode(created.code()).installmentList()).hasSize(2);
        assertThrows(EntityNotFoundException.class,
                () -> movementService.find(created.installmentList().get(2).movementCode()));

        final CardPurchase moved = purchaseService.update(created.code(), null, LocalDate.of(2094, 3, 25), null, null, null);

        assertThat(moved.installmentList()).extracting(CardInstallment::competenceDate)
                .containsExactly(LocalDate.of(2094, 5, 5), LocalDate.of(2094, 6, 5));

        purchaseService.delete(created.code());

        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("0");
        assertThrows(EntityNotFoundException.class, () -> purchaseService.findByCode(created.code()));
        assertThrows(EntityNotFoundException.class,
                () -> movementService.find(created.installmentList().get(0).movementCode()));
    }

    @Test
    void shouldNotLetAnInstallmentBeEditedOrDeletedAsAPlainMovement() {
        final CreditCard card = createCard("1000.00");
        final UUID movementCode = purchase(card, PURCHASE_DATE, 2, "100.00").installmentList().get(0).movementCode();

        assertThrows(ConflictException.class, () -> movementService.delete(movementCode));
        assertThrows(ConflictException.class, () -> movementService.update(new FinancialMovementUpdate(
                movementCode, null, "Outra descrição", null, null, null, null, null, null, null, null, false)));
        assertThat(movementService.find(movementCode).description()).isEqualTo("Compra 2x (1/2)");
    }

    @Test
    void shouldListInvoicesNewestFirstWithTheirInstallmentsAndOpenState() {
        final CreditCard card = createCard("1000.00");
        purchase(card, PURCHASE_DATE, 3, "100.00");

        final List<CardInvoice> invoices = invoiceService.find(card.code(), null);

        assertThat(invoices).extracting(CardInvoice::referenceMonth)
                .containsExactly(YearMonth.of(2094, 5), YearMonth.of(2094, 4), YearMonth.of(2094, 3));
        assertThat(invoices).extracting(CardInvoice::state).containsOnly(CardInvoiceState.OPEN);
        assertThat(invoiceService.find(card.code(), YearMonth.of(2094, 4))).hasSize(1);
        assertThat(invoiceService.find(card.code(), YearMonth.of(2095, 1))).isEmpty();

        final CardInvoice march = invoiceService.findByCode(invoices.get(2).code());
        assertThat(march.closingDate()).isEqualTo(LocalDate.of(2094, 3, 25));
        assertThat(march.dueDate()).isEqualTo(LocalDate.of(2094, 4, 5));
        assertThat(march.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(march.installments()).hasSize(1);
    }

    @Test
    void shouldShareAnInvoiceBetweenPurchasesAndSumTheirInstallments() {
        final CreditCard card = createCard(null);
        purchase(card, PURCHASE_DATE, 1, "40.00");
        purchase(card, LocalDate.of(2094, 3, 20), 2, "60.00");

        final CardInvoice march = invoiceService.find(card.code(), YearMonth.of(2094, 3)).get(0);

        assertThat(march.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoiceService.findByCode(march.code()).installments()).hasSize(2);
        assertThat(invoiceService.find(card.code(), null)).hasSize(2);
    }

    @Test
    void shouldRefuseRetroactivePurchaseInAPaidInvoiceAndFreezeThePaidPurchase() {
        final CreditCard card = createCard("1000.00");
        final CardPurchase created = purchase(card, PURCHASE_DATE, 1, "100.00");
        paymentService.pay(invoiceService.find(card.code(), YearMonth.of(2094, 3)).get(0).code(), null,
                LocalDate.of(2094, 4, 5));

        assertThat(invoiceService.find(card.code(), YearMonth.of(2094, 3)).get(0).state())
                .isEqualTo(CardInvoiceState.PAID);
        assertThat(cardService.limit(card.code()).used()).isEqualByComparingTo("0");
        assertThrows(ConflictException.class, () -> purchase(card, LocalDate.of(2094, 3, 11), 1, "10.00"));
        assertThrows(ConflictException.class,
                () -> purchaseService.update(created.code(), null, null, null, new BigDecimal("120.00"), null));
        assertThrows(ConflictException.class, () -> purchaseService.delete(created.code()));
    }
}
