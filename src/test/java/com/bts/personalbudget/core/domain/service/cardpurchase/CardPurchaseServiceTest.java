package com.bts.personalbudget.core.domain.service.cardpurchase;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardPurchaseServiceTest {

    @InjectMocks
    private CardPurchaseService service;

    @Mock
    private CardPurchaseRepository purchaseRepository;

    @Mock
    private CardInvoiceRepository invoiceRepository;

    @Mock
    private CardInstallmentRepository installmentRepository;

    @Mock
    private CreditCardRepository cardRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private PayCycleMovementGuard payCycleMovementGuard;

    @Spy
    private CardPurchaseMapper mapper = Mappers.getMapper(CardPurchaseMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private final CreditCardEntity card = CreditCardFactory.buildEntity();

    @BeforeEach
    void setUp() {
        card.setCreditLimit(new BigDecimal("1000.00"));
        when(cardRepository.findForUpdateByCode(card.getCode())).thenReturn(Optional.of(card));
        when(installmentRepository.sumUnpaidByCardId(card.getId())).thenReturn(BigDecimal.ZERO);
        final AtomicLong invoiceIds = new AtomicLong(500);
        when(invoiceRepository.findByCardIdAndReferenceMonth(anyLong(), any())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(CardInvoiceEntity.class))).thenAnswer(invocation -> {
            final CardInvoiceEntity invoice = invocation.getArgument(0);
            invoice.setId(invoiceIds.incrementAndGet());
            invoice.setCode(UUID.randomUUID());
            return invoice;
        });
        when(purchaseRepository.save(any(CardPurchaseEntity.class))).thenAnswer(invocation -> {
            final CardPurchaseEntity purchase = invocation.getArgument(0);
            purchase.setId(10L);
            purchase.setCode(CardPurchaseFactory.PURCHASE_CODE);
            return purchase;
        });
    }

    @SuppressWarnings("unchecked")
    private List<FinancialMovementEntity> savedInstallments() {
        final ArgumentCaptor<List<FinancialMovementEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(installmentRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- create

    @Test
    void shouldSplitThePurchaseIntoOneDebitInstallmentPerInvoiceWithCompetenceOnTheInvoiceDueDate() {
        final CardPurchase created = service.create(CardPurchaseFactory.buildNewModel());

        final List<FinancialMovementEntity> installments = savedInstallments();
        assertThat(installments).hasSize(3);
        assertThat(installments).extracting(FinancialMovementEntity::getInstallmentNumber).containsExactly(1, 2, 3);
        assertThat(installments).extracting(FinancialMovementEntity::getCompetenceDate)
                .containsExactly(LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5));
        assertThat(installments).extracting(movement -> movement.getDueDate().toLocalDate())
                .containsExactly(LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5));
        assertThat(installments).allSatisfy(movement -> {
            assertThat(movement.getOperationType()).isEqualTo(OperationType.DEBIT);
            assertThat(movement.getAmount()).isEqualByComparingTo("-100.00");
            assertThat(movement.getAmountPaid()).isEqualByComparingTo("0");
            assertThat(movement.getMovementDate().toLocalDate()).isEqualTo(CardPurchaseFactory.PURCHASE_DATE);
            assertThat(movement.getStatus()).isEqualTo(FinancialMovementStatus.PENDING);
            assertThat(movement.getCompetenceOverridden()).isTrue();
            assertThat(movement.getCardPurchaseId()).isEqualTo(10L);
            assertThat(movement.getCardInvoiceId()).isNotNull();
        });
        assertThat(installments.get(0).getDescription()).isEqualTo("Geladeira (1/3)");
        assertThat(created.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(created.installmentList()).hasSize(3);
        assertThat(created.installmentList().get(2).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldPutAPurchaseOnTheClosingDateInTheNextInvoice() {
        final CardPurchase onClosing = new CardPurchase(null, card.getCode(), "Notebook", LocalDate.of(2026, 11, 25),
                1, AMOUNT, null, null, null);

        service.create(onClosing);

        assertThat(savedInstallments().get(0).getCompetenceDate()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void shouldLockTheCycleGuardBeforeTheCardAndRequireEditableCompetences() {
        service.create(CardPurchaseFactory.buildNewModel());

        final org.mockito.InOrder order = org.mockito.Mockito.inOrder(payCycleMovementGuard, cardRepository);
        order.verify(payCycleMovementGuard).lock();
        order.verify(cardRepository).findForUpdateByCode(card.getCode());
        verify(payCycleMovementGuard).requireEditable(LocalDate.of(2026, 12, 5));
        verify(payCycleMovementGuard).requireEditable(LocalDate.of(2027, 1, 5));
        verify(payCycleMovementGuard).requireEditable(LocalDate.of(2027, 2, 5));
    }

    @Test
    void shouldRefusePurchaseWhenACompetenceFallsInAClosedCycle() {
        doThrow(new ConflictException("O ciclo está fechado")).when(payCycleMovementGuard)
                .requireEditable(LocalDate.of(2027, 1, 5));

        assertThrows(ConflictException.class, () -> service.create(CardPurchaseFactory.buildNewModel()));

        verify(purchaseRepository, never()).save(any());
        verify(installmentRepository, never()).saveAll(any());
    }

    @Test
    void shouldRefusePurchaseWhenTheTotalPassesTheAvailableLimit() {
        when(installmentRepository.sumUnpaidByCardId(card.getId())).thenReturn(new BigDecimal("800.00"));

        final ConflictException exception =
                assertThrows(ConflictException.class, () -> service.create(CardPurchaseFactory.buildNewModel()));

        assertThat(exception.getMessage()).contains("R$ 200.00").contains("R$ 300.00");
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldAcceptPurchaseThatUsesExactlyTheAvailableLimit() {
        when(installmentRepository.sumUnpaidByCardId(card.getId())).thenReturn(new BigDecimal("700.00"));

        service.create(CardPurchaseFactory.buildNewModel());

        verify(purchaseRepository).save(any());
    }

    @Test
    void shouldNotCheckTheLimitOfACardWithoutLimit() {
        card.setCreditLimit(null);

        service.create(CardPurchaseFactory.buildNewModel());

        verify(installmentRepository, never()).sumUnpaidByCardId(anyLong());
        verify(purchaseRepository).save(any());
    }

    @Test
    void shouldRefuseAnInstallmentThatFallsInAPaidInvoice() {
        final CardInvoiceEntity paid = CardPurchaseFactory.buildInvoice(card, 1);
        when(invoiceRepository.findByCardIdAndReferenceMonth(card.getId(), LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.of(paid));
        when(installmentRepository.countActiveByInvoiceId(paid.getId())).thenReturn(2L);
        when(installmentRepository.countActiveByInvoiceIdAndStatus(paid.getId(), FinancialMovementStatus.PAID_OUT))
                .thenReturn(2L);

        assertThrows(ConflictException.class, () -> service.create(CardPurchaseFactory.buildNewModel()));

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldAcceptAnInstallmentInAnInvoiceThatIsOnlyClosedOrPartiallyPaid() {
        final CardInvoiceEntity partial = CardPurchaseFactory.buildInvoice(card, 1);
        when(invoiceRepository.findByCardIdAndReferenceMonth(card.getId(), LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.of(partial));
        when(installmentRepository.countActiveByInvoiceId(partial.getId())).thenReturn(2L);
        when(installmentRepository.countActiveByInvoiceIdAndStatus(partial.getId(), FinancialMovementStatus.PAID_OUT))
                .thenReturn(1L);

        service.create(CardPurchaseFactory.buildNewModel());

        assertThat(savedInstallments().get(0).getCardInvoiceId()).isEqualTo(partial.getId());
        verify(invoiceRepository, org.mockito.Mockito.times(2)).save(any(CardInvoiceEntity.class));
    }

    @Test
    void shouldKeepTheDatesOfAnExistingInvoiceEvenIfTheCardDaysChangedLater() {
        final CardInvoiceEntity existing = CardPurchaseFactory.buildInvoice(card, 1);
        existing.setDueDate(LocalDate.of(2026, 12, 10));
        when(invoiceRepository.findByCardIdAndReferenceMonth(card.getId(), LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.of(existing));

        service.create(CardPurchaseFactory.buildNewModel());

        assertThat(savedInstallments().get(0).getCompetenceDate()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void shouldMarkAnInstallmentLateWhenItsInvoiceIsAlreadyPastDue() {
        final CardPurchase old = new CardPurchase(null, card.getCode(), "Antiga", LocalDate.of(2026, 3, 1), 1,
                AMOUNT, null, null, null);

        service.create(old);

        assertThat(savedInstallments().get(0).getStatus()).isEqualTo(FinancialMovementStatus.LATE);
    }

    @Test
    void shouldRejectAnInactiveOrUnknownCard() {
        card.setStatus(CreditCardStatus.INACTIVE);
        assertThrows(InvalidFieldsException.class, () -> service.create(CardPurchaseFactory.buildNewModel()));

        final CardPurchase unknown = new CardPurchase(null, UUID.randomUUID(), "Geladeira",
                CardPurchaseFactory.PURCHASE_DATE, 3, AMOUNT, null, null, null);
        assertThrows(InvalidFieldsException.class, () -> service.create(unknown));

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidFields() {
        final UUID cardCode = card.getCode();
        final LocalDate date = CardPurchaseFactory.PURCHASE_DATE;
        final List<CardPurchase> invalid = List.of(
                new CardPurchase(null, cardCode, " ", date, 3, AMOUNT, null, null, null),
                new CardPurchase(null, cardCode, "x".repeat(101), date, 3, AMOUNT, null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", null, 3, AMOUNT, null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", date, 0, AMOUNT, null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", date, 61, AMOUNT, null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", date, 3, BigDecimal.ZERO, null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", date, 3, new BigDecimal("10.001"), null, null, null),
                new CardPurchase(null, cardCode, "Geladeira", date, 3, new BigDecimal("100000000.00"), null, null, null),
                new CardPurchase(null, null, "Geladeira", date, 3, AMOUNT, null, null, null));

        invalid.forEach(purchase -> assertThrows(InvalidFieldsException.class, () -> service.create(purchase)));

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldValidateTheCategoryAsDebit() {
        final UUID category = UUID.randomUUID();
        final CardPurchase withCategory = new CardPurchase(null, card.getCode(), "Geladeira",
                CardPurchaseFactory.PURCHASE_DATE, 3, AMOUNT, null, category, null);

        service.create(withCategory);

        verify(categoryService).validateForUse(category, OperationType.DEBIT);
        assertThat(savedInstallments()).allSatisfy(movement -> assertThat(movement.getCategoryCode()).isEqualTo(category));
    }

    // ---------------------------------------------------------------- read

    @Test
    void shouldAnswerNotFoundForAnUnknownOrDeletedPurchase() {
        when(purchaseRepository.findActiveByCode(CardPurchaseFactory.PURCHASE_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findByCode(CardPurchaseFactory.PURCHASE_CODE));
        assertThrows(EntityNotFoundException.class, () -> service.delete(CardPurchaseFactory.PURCHASE_CODE));
        assertThrows(EntityNotFoundException.class, () -> service.update(CardPurchaseFactory.PURCHASE_CODE,
                "Outra", null, null, null, null));
    }

    @Test
    void shouldRejectInvalidOrTooLongPeriods() {
        final LocalDate start = LocalDate.of(2026, 1, 1);

        assertThrows(InvalidFieldsException.class, () -> service.find(null, start, null));
        assertThrows(InvalidFieldsException.class, () -> service.find(start, start.minusDays(1), null));
        assertThrows(InvalidFieldsException.class, () -> service.find(start, start.plusDays(367), null));
    }

    // ---------------------------------------------------------------- update / delete

    private CardPurchaseEntity stubExistingPurchase() {
        final CardPurchaseEntity purchase = CardPurchaseFactory.buildEntity(card);
        final List<CardInvoiceEntity> invoices = List.of(CardPurchaseFactory.buildInvoice(card, 1),
                CardPurchaseFactory.buildInvoice(card, 2), CardPurchaseFactory.buildInvoice(card, 3));
        when(purchaseRepository.findActiveByCode(purchase.getCode())).thenReturn(Optional.of(purchase));
        when(installmentRepository.findActiveByPurchaseId(purchase.getId())).thenReturn(List.of(
                CardPurchaseFactory.buildInstallment(purchase, invoices.get(0), 1),
                CardPurchaseFactory.buildInstallment(purchase, invoices.get(1), 2),
                CardPurchaseFactory.buildInstallment(purchase, invoices.get(2), 3)));
        when(invoiceRepository.findAllWithCardByIdIn(any())).thenReturn(invoices);
        when(installmentRepository.sumUnpaidByCardId(card.getId())).thenReturn(new BigDecimal("300.00"));
        return purchase;
    }

    @Test
    void shouldUpdateOnlyTheDescriptionOfTheInstallmentsWithoutTouchingCyclesOrLimit() {
        final CardPurchaseEntity purchase = stubExistingPurchase();

        final CardPurchase updated = service.update(purchase.getCode(), "Geladeira inverter", null, null, null, null);

        assertThat(updated.installmentList()).extracting(CardInstallment::description)
                .containsExactly("Geladeira inverter (1/3)", "Geladeira inverter (2/3)", "Geladeira inverter (3/3)");
        verify(payCycleMovementGuard, never()).requireEditable(any());
        verify(installmentRepository, never()).sumUnpaidByCardId(anyLong());
    }

    @Test
    void shouldDoNothingWhenNothingChanged() {
        final CardPurchaseEntity purchase = stubExistingPurchase();

        service.update(purchase.getCode(), "Geladeira", CardPurchaseFactory.PURCHASE_DATE, 3,
                new BigDecimal("100.0"), null);

        verify(payCycleMovementGuard, never()).requireEditable(any());
    }

    @Test
    void shouldRecalculateInPlaceAndDeleteExtraInstallmentsWhenTheyShrink() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        final List<FinancialMovementEntity> current = installmentRepository.findActiveByPurchaseId(purchase.getId());

        final CardPurchase updated = service.update(purchase.getCode(), null, null, 2, new BigDecimal("150.00"), null);

        assertThat(updated.installmentList()).hasSize(2);
        assertThat(current.get(0).getAmount()).isEqualByComparingTo("-150.00");
        assertThat(current.get(1).getAmount()).isEqualByComparingTo("-150.00");
        assertThat(current.get(2).getFlagActive()).isFalse();
        assertThat(current.get(0).getDescription()).isEqualTo("Geladeira (1/2)");
        assertThat(purchase.getInstallments()).isEqualTo(2);
        assertThat(purchase.getInstallmentAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldCreateTheMissingInstallmentsWhenTheyGrow() {
        final CardPurchaseEntity purchase = stubExistingPurchase();

        final CardPurchase updated = service.update(purchase.getCode(), null, null, 4, null, null);

        assertThat(updated.installmentList()).hasSize(4);
        final List<FinancialMovementEntity> created = savedInstallments();
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getInstallmentNumber()).isEqualTo(4);
        assertThat(created.get(0).getCompetenceDate()).isEqualTo(LocalDate.of(2027, 3, 5));
    }

    @Test
    void shouldReplaceOwnUnpaidAmountWhenCheckingTheLimitOnAnEdit() {
        final CardPurchaseEntity purchase = stubExistingPurchase();

        // used 300.00 includes this purchase; 10 x 100.00 = 1000.00 fits a 1000.00 limit once it is released
        service.update(purchase.getCode(), null, null, 10, null, null);

        assertThat(purchase.getInstallments()).isEqualTo(10);
    }

    @Test
    void shouldRefuseAnEditThatPassesTheLimit() {
        final CardPurchaseEntity purchase = stubExistingPurchase();

        assertThrows(ConflictException.class,
                () -> service.update(purchase.getCode(), null, null, 11, null, null));

        assertThat(purchase.getInstallments()).isEqualTo(3);
    }

    @Test
    void shouldRefuseToChangeTheScheduleOfAPurchaseWithAPaidInstallment() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        final FinancialMovementEntity first = installmentRepository.findActiveByPurchaseId(purchase.getId()).get(0);
        first.setAmountPaid(new BigDecimal("-40.00"));

        assertThrows(ConflictException.class,
                () -> service.update(purchase.getCode(), null, null, null, new BigDecimal("120.00"), null));

        assertThat(purchase.getInstallmentAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldAllowADescriptionChangeEvenWithAPaidInstallment() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        installmentRepository.findActiveByPurchaseId(purchase.getId()).get(0).setStatus(FinancialMovementStatus.PAID_OUT);

        final CardPurchase updated = service.update(purchase.getCode(), "Outra", null, null, null, null);

        assertThat(updated.description()).isEqualTo("Outra");
    }

    @Test
    void shouldRequireEditableCyclesAndValidateTheCategoryOnACategoryChange() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        final UUID category = UUID.randomUUID();

        final CardPurchase updated = service.update(purchase.getCode(), null, null, null, null, category);

        verify(categoryService).validateForUse(category, OperationType.DEBIT);
        verify(payCycleMovementGuard).requireEditable(LocalDate.of(2026, 12, 5));
        assertThat(updated.categoryCode()).isEqualTo(category);
    }

    @Test
    void shouldSoftDeleteThePurchaseAndItsInstallments() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        final List<FinancialMovementEntity> current = installmentRepository.findActiveByPurchaseId(purchase.getId());

        service.delete(purchase.getCode());

        assertThat(purchase.isFlagActive()).isFalse();
        assertThat(current).allSatisfy(movement -> assertThat(movement.getFlagActive()).isFalse());
        verify(payCycleMovementGuard).requireEditable(LocalDate.of(2027, 1, 5));
    }

    @Test
    void shouldRefuseToDeleteAPurchaseInAClosedCycle() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        doThrow(new ConflictException("O ciclo está fechado")).when(payCycleMovementGuard)
                .requireEditable(LocalDate.of(2027, 1, 5));

        assertThrows(ConflictException.class, () -> service.delete(purchase.getCode()));

        assertThat(purchase.isFlagActive()).isTrue();
    }

    @Test
    void shouldRefuseToDeleteAPurchaseWithAPayment() {
        final CardPurchaseEntity purchase = stubExistingPurchase();
        installmentRepository.findActiveByPurchaseId(purchase.getId()).get(0).setStatus(FinancialMovementStatus.PAID_OUT);

        assertThrows(ConflictException.class, () -> service.delete(purchase.getCode()));

        assertThat(purchase.isFlagActive()).isTrue();
    }
}
