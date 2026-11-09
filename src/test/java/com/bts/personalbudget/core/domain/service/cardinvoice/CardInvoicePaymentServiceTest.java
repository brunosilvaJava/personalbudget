package com.bts.personalbudget.core.domain.service.cardinvoice;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.factory.AccountFactory;
import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CardInvoicePaymentMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CardInvoicePaymentRepository;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import com.bts.personalbudget.repository.MovementSettlementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardInvoicePaymentServiceTest {

    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 12, 5);

    @InjectMocks
    private CardInvoicePaymentService service;

    @Mock
    private CardInvoiceRepository invoiceRepository;

    @Mock
    private CardInvoicePaymentRepository paymentRepository;

    @Mock
    private CardInstallmentRepository installmentRepository;

    @Mock
    private MovementSettlementRepository settlementRepository;

    @Mock
    private CreditCardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MovementSettlementService settlementService;

    @Mock
    private PayCycleMovementGuard payCycleMovementGuard;

    @Spy
    private CardInvoicePaymentMapper mapper = Mappers.getMapper(CardInvoicePaymentMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-12-05T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final CreditCardEntity card = CreditCardFactory.buildEntity();
    private final CardPurchaseEntity purchase = CardPurchaseFactory.buildEntity(card);
    private final CardInvoiceEntity invoice = CardPurchaseFactory.buildInvoice(card, 1);
    private final AccountEntity defaultAccount = AccountFactory.buildEntity();

    private void givenInvoiceWith(final FinancialMovementEntity... installments) {
        when(invoiceRepository.findByCode(invoice.getCode())).thenReturn(Optional.of(invoice));
        when(cardRepository.findForUpdateByCode(card.getCode())).thenReturn(Optional.of(card));
        when(paymentRepository.existsActiveByInvoiceId(invoice.getId())).thenReturn(false);
        when(installmentRepository.findForUpdateActiveByInvoiceId(invoice.getId()))
                .thenReturn(List.of(installments));
        when(accountRepository.findByDefaultAccountTrue()).thenReturn(Optional.of(defaultAccount));
        when(paymentRepository.save(any(CardInvoicePaymentEntity.class))).thenAnswer(invocation -> {
            final CardInvoicePaymentEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            entity.prePersist();
            return entity;
        });
    }

    private FinancialMovementEntity installment(final int k, final String paid) {
        final FinancialMovementEntity movement = CardPurchaseFactory.buildInstallment(purchase, invoice, k);
        movement.setAmountPaid(new BigDecimal(paid));
        return movement;
    }

    @Test
    void shouldPayWhatIsStillOwedOnEveryInstallmentInTheDefaultAccountAndTakeTheLocksInOrder() {
        final FinancialMovementEntity first = installment(1, "0.00");
        final FinancialMovementEntity second = installment(2, "-60.00");
        givenInvoiceWith(first, second);

        final CardInvoicePayment payment = service.pay(invoice.getCode(), null, PAYMENT_DATE);

        assertThat(payment.amount()).isEqualByComparingTo("140.00");
        assertThat(payment.paymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(payment.accountCode()).isEqualTo(AccountFactory.ACCOUNT_CODE);
        assertThat(payment.invoiceCode()).isEqualTo(invoice.getCode());
        assertThat(payment.reversedAt()).isNull();
        verify(settlementService).settleRemainingForInvoicePayment(eq(first), eq(defaultAccount), eq(PAYMENT_DATE),
                any(CardInvoicePaymentEntity.class));
        verify(settlementService).settleRemainingForInvoicePayment(eq(second), eq(defaultAccount), eq(PAYMENT_DATE),
                any(CardInvoicePaymentEntity.class));
        final InOrder order = inOrder(payCycleMovementGuard, cardRepository, installmentRepository);
        order.verify(payCycleMovementGuard).lock();
        order.verify(cardRepository).findForUpdateByCode(card.getCode());
        order.verify(installmentRepository).findForUpdateActiveByInvoiceId(invoice.getId());
    }

    @Test
    void shouldPreferTheInformedAccountThenTheCardDefaultAccountOverTheDefaultOne() {
        final AccountEntity informed = AccountFactory.buildEntity(2L, AccountFactory.OTHER_ACCOUNT_CODE, "Outra", false);
        final AccountEntity cardAccount = AccountFactory.buildEntity(3L, UUID.randomUUID(), "Da fatura", false);
        givenInvoiceWith(installment(1, "0.00"));
        when(accountRepository.findByCode(informed.getCode())).thenReturn(Optional.of(informed));

        assertThat(service.pay(invoice.getCode(), informed.getCode(), PAYMENT_DATE).accountCode())
                .isEqualTo(informed.getCode());

        card.setDefaultAccount(cardAccount);
        assertThat(service.pay(invoice.getCode(), null, PAYMENT_DATE).accountCode())
                .isEqualTo(cardAccount.getCode());
    }

    @Test
    void shouldRefuseToPayAnInvoiceThatAlreadyHasAnActivePayment() {
        givenInvoiceWith(installment(1, "0.00"));
        when(paymentRepository.existsActiveByInvoiceId(invoice.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.pay(invoice.getCode(), null, PAYMENT_DATE));

        verify(paymentRepository, never()).save(any());
        verify(installmentRepository, never()).findForUpdateActiveByInvoiceId(any());
    }

    @Test
    void shouldRefuseToPayAnInvoiceWithoutInstallmentsOrWithNothingLeftToPay() {
        givenInvoiceWith();
        assertThrows(ConflictException.class, () -> service.pay(invoice.getCode(), null, PAYMENT_DATE));

        givenInvoiceWith(installment(1, "-100.00"));
        assertThrows(ConflictException.class, () -> service.pay(invoice.getCode(), null, PAYMENT_DATE));

        verify(paymentRepository, never()).save(any());
        verify(settlementService, never()).settleRemainingForInvoicePayment(any(), any(), any(), any());
    }

    @Test
    void shouldRejectAMissingDateAnUnknownOrInactiveAccountAndADateBeforeTheInitialBalance() {
        givenInvoiceWith(installment(1, "0.00"));

        assertThrows(InvalidFieldsException.class, () -> service.pay(invoice.getCode(), null, null));

        assertThrows(InvalidFieldsException.class,
                () -> service.pay(invoice.getCode(), UUID.randomUUID(), PAYMENT_DATE));

        defaultAccount.setStatus(AccountStatus.INACTIVE);
        assertThrows(InvalidFieldsException.class, () -> service.pay(invoice.getCode(), null, PAYMENT_DATE));

        defaultAccount.setStatus(AccountStatus.ACTIVE);
        assertThrows(InvalidFieldsException.class,
                () -> service.pay(invoice.getCode(), null, AccountFactory.INITIAL_BALANCE_DATE.minusDays(1)));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldReturnNotFoundForAnUnknownInvoice() {
        when(invoiceRepository.findByCode(invoice.getCode())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.pay(invoice.getCode(), null, PAYMENT_DATE));
        assertThrows(EntityNotFoundException.class, () -> service.findAll(invoice.getCode()));
        assertThrows(EntityNotFoundException.class,
                () -> service.reverse(invoice.getCode(), CardPurchaseFactory.PAYMENT_CODE));
    }

    @Test
    void shouldReverseEverySettlementOfThePaymentAndMarkItReversedWithoutDeletingIt() {
        final CardInvoicePaymentEntity payment = CardPurchaseFactory.buildPaymentEntity(invoice);
        final MovementSettlementEntity first = MovementSettlementEntity.builder().id(1L).build();
        final MovementSettlementEntity second = MovementSettlementEntity.builder().id(2L).build();
        when(invoiceRepository.findByCode(invoice.getCode())).thenReturn(Optional.of(invoice));
        when(cardRepository.findForUpdateByCode(card.getCode())).thenReturn(Optional.of(card));
        when(paymentRepository.findForUpdateByCodeAndInvoiceId(payment.getCode(), invoice.getId()))
                .thenReturn(Optional.of(payment));
        when(settlementRepository.findActiveByInvoicePaymentId(payment.getId())).thenReturn(List.of(first, second));

        final CardInvoicePayment reversed = service.reverse(invoice.getCode(), payment.getCode());

        assertThat(reversed.reversedAt()).isEqualTo(LocalDateTime.of(2026, 12, 5, 12, 0));
        verify(settlementService).reverseForInvoicePayment(first);
        verify(settlementService).reverseForInvoicePayment(second);
        verify(paymentRepository, never()).delete(any(CardInvoicePaymentEntity.class));
        final InOrder order = inOrder(payCycleMovementGuard, cardRepository, installmentRepository);
        order.verify(payCycleMovementGuard).lock();
        order.verify(cardRepository).findForUpdateByCode(card.getCode());
        order.verify(installmentRepository).findForUpdateActiveByInvoiceId(invoice.getId());
    }

    @Test
    void shouldRefuseToReverseTwiceAndReturnNotFoundForAnotherInvoicesPayment() {
        final CardInvoicePaymentEntity payment = CardPurchaseFactory.buildPaymentEntity(invoice);
        payment.setReversedAt(LocalDateTime.of(2026, 12, 6, 9, 0));
        when(invoiceRepository.findByCode(invoice.getCode())).thenReturn(Optional.of(invoice));
        when(cardRepository.findForUpdateByCode(card.getCode())).thenReturn(Optional.of(card));
        when(paymentRepository.findForUpdateByCodeAndInvoiceId(payment.getCode(), invoice.getId()))
                .thenReturn(Optional.of(payment));

        assertThrows(ConflictException.class, () -> service.reverse(invoice.getCode(), payment.getCode()));
        assertThrows(EntityNotFoundException.class, () -> service.reverse(invoice.getCode(), UUID.randomUUID()));

        verify(settlementService, never()).reverseForInvoicePayment(any());
    }

    @Test
    void shouldListThePaymentsOfTheInvoiceIncludingReversedOnes() {
        final CardInvoicePaymentEntity active = CardPurchaseFactory.buildPaymentEntity(invoice);
        final CardInvoicePaymentEntity reversed = CardPurchaseFactory.buildPaymentEntity(invoice);
        reversed.setId(800L);
        reversed.setCode(UUID.randomUUID());
        reversed.setReversedAt(LocalDateTime.of(2026, 12, 6, 9, 0));
        when(invoiceRepository.findByCode(invoice.getCode())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findAllByInvoiceId(invoice.getId())).thenReturn(List.of(active, reversed));

        final List<CardInvoicePayment> payments = service.findAll(invoice.getCode());

        assertThat(payments).extracting(CardInvoicePayment::reversedAt).containsExactly(null, reversed.getReversedAt());
    }
}
