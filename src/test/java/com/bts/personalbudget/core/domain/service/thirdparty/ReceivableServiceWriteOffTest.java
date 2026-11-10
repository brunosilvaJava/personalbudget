package com.bts.personalbudget.core.domain.service.thirdparty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.ReceivableWriteOffEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.ReceivableStatus;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CategoryRepository;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.ReceivableWriteOffRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** A write-off gives up on what a third party owes: an own expense, in one fixed category, with no cash. */
@ExtendWith(MockitoExtension.class)
class ReceivableServiceWriteOffTest {
    private static final UUID THIRD_PARTY_CODE = UUID.fromString("cccccccc-0000-0000-0000-000000000501");
    private static final UUID RECEIVABLE_CODE = UUID.fromString("dddddddd-0000-0000-0000-000000000501");
    private static final LocalDate WRITE_OFF_DATE = LocalDate.of(2026, 11, 10);

    @InjectMocks
    private ReceivableService service;

    @Mock private FinancialMovementRepository movementRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private CardPurchaseRepository purchaseRepository;
    @Mock private CardInvoiceRepository invoiceRepository;
    @Mock private MovementSettlementService settlementService;
    @Mock private PayCycleMovementGuard payCycleMovementGuard;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReceivableWriteOffRepository writeOffRepository;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldWriteOffTheOpenBalanceAsOwnExpenseInTheFixedCategory() {
        final FinancialMovementEntity receivable = receivable("100.00", "30.00", null);
        final CategoryEntity category = CategoryEntity.builder().id(9L)
                .code(ReceivableWriteOffCategory.CODE).name("Perdas com terceiros").build();
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(RECEIVABLE_CODE))
                .thenReturn(Optional.of(receivable));
        when(categoryRepository.findByCode(ReceivableWriteOffCategory.CODE)).thenReturn(Optional.of(category));
        when(movementRepository.save(receivable)).thenReturn(receivable);
        when(movementRepository.findById(5L)).thenReturn(Optional.of(
                FinancialMovementEntity.builder().id(5L).description("Compra").build()));

        final Receivable result = service.writeOff(THIRD_PARTY_CODE, RECEIVABLE_CODE, WRITE_OFF_DATE);

        final InOrder order = inOrder(payCycleMovementGuard, writeOffRepository);
        order.verify(payCycleMovementGuard).lock();
        order.verify(payCycleMovementGuard).requireEditable(WRITE_OFF_DATE);
        final ArgumentCaptor<ReceivableWriteOffEntity> saved = ArgumentCaptor.forClass(ReceivableWriteOffEntity.class);
        order.verify(writeOffRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("70.00");
        assertThat(saved.getValue().getEffectiveDate()).isEqualTo(WRITE_OFF_DATE);
        assertThat(saved.getValue().getCategory()).isSameAs(category);
        assertThat(saved.getValue().getReceivable()).isSameAs(receivable);
        assertThat(receivable.getWrittenOffAmount()).isEqualByComparingTo("70.00");
        assertThat(receivable.getStatus()).isEqualTo(FinancialMovementStatus.PAID_OUT);
        assertThat(result.status()).isEqualTo(ReceivableStatus.WRITTEN_OFF);
        assertThat(result.remainingAmount()).isEqualByComparingTo("0");
        assertThat(result.receivedAmount()).isEqualByComparingTo("30.00");
        assertThat(result.writtenOffAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldRefuseWriteOffWhenNothingIsOpen() {
        final FinancialMovementEntity receivable = receivable("100.00", "100.00", null);
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(RECEIVABLE_CODE))
                .thenReturn(Optional.of(receivable));

        assertThrows(ConflictException.class,
                () -> service.writeOff(THIRD_PARTY_CODE, RECEIVABLE_CODE, WRITE_OFF_DATE));

        verify(writeOffRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldRefuseWriteOffInAClosedCycle() {
        final FinancialMovementEntity receivable = receivable("100.00", "0.00", null);
        when(movementRepository.findForUpdateByCodeAndFlagActiveTrue(RECEIVABLE_CODE))
                .thenReturn(Optional.of(receivable));
        doThrow(new ConflictException("O ciclo está fechado")).when(payCycleMovementGuard)
                .requireEditable(WRITE_OFF_DATE);

        assertThrows(ConflictException.class,
                () -> service.writeOff(THIRD_PARTY_CODE, RECEIVABLE_CODE, WRITE_OFF_DATE));

        verify(writeOffRepository, never()).save(any());
        assertThat(receivable.getWrittenOffAmount()).isNull();
    }

    @Test
    void shouldRequireTheEffectiveDateToWriteOff() {
        assertThrows(InvalidFieldsException.class,
                () -> service.writeOff(THIRD_PARTY_CODE, RECEIVABLE_CODE, null));

        verifyNoInteractions(payCycleMovementGuard, writeOffRepository, categoryRepository);
    }

    @Test
    void shouldNotChangeAReceivableThatWasWrittenOff() {
        final FinancialMovementEntity writtenOff = receivable("100.00", "0.00", "100.00");
        final FinancialMovementEntity source = FinancialMovementEntity.builder().id(5L).build();
        when(movementRepository.findActiveReceivableBySourceMovementId(5L)).thenReturn(Optional.of(writtenOff));

        final ConflictException error = assertThrows(ConflictException.class,
                () -> service.syncForInstallment(source));

        assertThat(error.getMessage()).contains("baixa");
        assertThat(writtenOff.getWrittenOffAmount()).isEqualByComparingTo("100.00");
    }

    private static FinancialMovementEntity receivable(final String amount, final String paid, final String writtenOff) {
        final ThirdPartyEntity thirdParty = ThirdPartyEntity.builder().id(1L).code(THIRD_PARTY_CODE).name("João").build();
        return FinancialMovementEntity.builder().id(10L).code(RECEIVABLE_CODE)
                .amount(new BigDecimal(amount)).amountPaid(new BigDecimal(paid))
                .writtenOffAmount(writtenOff == null ? null : new BigDecimal(writtenOff))
                .dueDate(LocalDateTime.of(2026, 11, 5, 0, 0)).competenceDate(LocalDate.of(2026, 11, 5))
                .status(FinancialMovementStatus.LATE).thirdParty(thirdParty).receivableSourceMovementId(5L).build();
    }
}
