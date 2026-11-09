package com.bts.personalbudget.core.domain.service.cardinvoice;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.factory.CardPurchaseFactory;
import com.bts.personalbudget.core.domain.factory.CreditCardFactory;
import com.bts.personalbudget.mapper.CardInvoiceMapper;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardInvoiceServiceTest {

    @InjectMocks
    private CardInvoiceService service;

    @Mock
    private CardInvoiceRepository invoiceRepository;

    @Mock
    private CardInstallmentRepository installmentRepository;

    @Mock
    private CardPurchaseRepository purchaseRepository;

    @Mock
    private CreditCardRepository cardRepository;

    @Spy
    private CardInvoiceMapper mapper = Mappers.getMapper(CardInvoiceMapper.class);

    @Spy
    private CardPurchaseMapper purchaseMapper = Mappers.getMapper(CardPurchaseMapper.class);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final CreditCardEntity card = CreditCardFactory.buildEntity();
    private final CardPurchaseEntity purchase = CardPurchaseFactory.buildEntity(card);
    // closing 2026-11-25, due 2026-12-05
    private final CardInvoiceEntity november = CardPurchaseFactory.buildInvoice(card, 1);
    // closing 2026-10-25 (already passed on 2026-11-08), due 2026-11-05
    private final CardInvoiceEntity october = CardPurchaseFactory.buildInvoice(card, 0);

    private FinancialMovementEntity installment(final CardInvoiceEntity invoice, final int k,
                                                final String paid, final FinancialMovementStatus status) {
        final FinancialMovementEntity movement = CardPurchaseFactory.buildInstallment(purchase, invoice, k);
        movement.setAmountPaid(new BigDecimal(paid));
        movement.setStatus(status);
        return movement;
    }

    @Test
    void shouldListInvoicesNewestFirstWithDerivedStateAndTotals() {
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(invoiceRepository.findByCardId(card.getId(), null)).thenReturn(List.of(november, october));
        when(installmentRepository.findActiveByInvoiceIdIn(any())).thenReturn(List.of(
                installment(november, 1, "0.00", FinancialMovementStatus.PENDING),
                installment(october, 2, "-100.00", FinancialMovementStatus.PAID_OUT)));

        final List<CardInvoice> invoices = service.find(card.getCode(), null);

        assertThat(invoices).extracting(CardInvoice::state).containsExactly(CardInvoiceState.OPEN, CardInvoiceState.PAID);
        assertThat(invoices.get(0).totalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoices.get(0).paidAmount()).isEqualByComparingTo("0");
        assertThat(invoices.get(1).paidAmount()).isEqualByComparingTo("100.00");
        assertThat(invoices).allSatisfy(invoice -> assertThat(invoice.installments()).isEmpty());
    }

    @Test
    void shouldDeriveClosedWhenTheClosingDatePassedAndTheInvoiceIsNotPaid() {
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(invoiceRepository.findByCardId(card.getId(), null)).thenReturn(List.of(october));
        when(installmentRepository.findActiveByInvoiceIdIn(any())).thenReturn(List.of(
                installment(october, 1, "-40.00", FinancialMovementStatus.LATE)));

        final CardInvoice invoice = service.find(card.getCode(), null).get(0);

        assertThat(invoice.state()).isEqualTo(CardInvoiceState.CLOSED);
        assertThat(invoice.paidAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldNotCallAnInvoiceWithoutInstallmentsPaid() {
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(invoiceRepository.findByCardId(card.getId(), null)).thenReturn(List.of(october));

        final CardInvoice invoice = service.find(card.getCode(), null).get(0);

        assertThat(invoice.state()).isEqualTo(CardInvoiceState.CLOSED);
        assertThat(invoice.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void shouldFilterByReferenceMonth() {
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.of(card));
        when(invoiceRepository.findByCardId(card.getId(), LocalDate.of(2026, 11, 1))).thenReturn(List.of(november));
        when(installmentRepository.findActiveByInvoiceIdIn(any())).thenReturn(List.of());

        final List<CardInvoice> invoices = service.find(card.getCode(), YearMonth.of(2026, 11));

        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).referenceMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(invoices.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(service.find(card.getCode(), YearMonth.of(2030, 1))).isEmpty();
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownCardOrInvoice() {
        when(cardRepository.findByCode(card.getCode())).thenReturn(Optional.empty());
        when(invoiceRepository.findByCode(CardPurchaseFactory.INVOICE_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.find(card.getCode(), null));
        assertThrows(EntityNotFoundException.class, () -> service.findByCode(CardPurchaseFactory.INVOICE_CODE));
    }

    @Test
    void shouldReturnTheInvoiceDetailWithItsInstallments() {
        final UUID code = november.getCode();
        when(invoiceRepository.findByCode(code)).thenReturn(Optional.of(november));
        when(installmentRepository.findActiveByInvoiceId(november.getId())).thenReturn(List.of(
                installment(november, 1, "0.00", FinancialMovementStatus.PENDING)));
        when(purchaseRepository.findAllWithCardByIdIn(any())).thenReturn(List.of(purchase));

        final CardInvoice invoice = service.findByCode(code);

        assertThat(invoice.installments()).hasSize(1);
        assertThat(invoice.installments().get(0).purchaseCode()).isEqualTo(purchase.getCode());
        assertThat(invoice.installments().get(0).installmentCount()).isEqualTo(3);
        assertThat(invoice.installments().get(0).description()).isEqualTo("Geladeira (1/3)");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.state()).isEqualTo(CardInvoiceState.OPEN);
    }

    @Test
    void shouldDeriveThePaidStateOnlyWhenEveryInstallmentIsPaid() {
        assertThat(CardInvoiceState.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 8), true))
                .isEqualTo(CardInvoiceState.PAID);
        assertThat(CardInvoiceState.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 24), false))
                .isEqualTo(CardInvoiceState.OPEN);
        assertThat(CardInvoiceState.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25), false))
                .isEqualTo(CardInvoiceState.CLOSED);
    }
}
