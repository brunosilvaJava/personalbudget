package com.bts.personalbudget.core.domain.service.cardinvoice;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays and reverses a whole card invoice (E9c). Paying settles what is still owed on every
 * installment of the invoice, in one account and on one date, so the cash moves once, on the
 * payment date, and no consumption is created: the installments were already the consumption.
 *
 * <p>Lock order, always: pay-cycle guard, then the card row, then the installments of the invoice.
 * The card row serializes a payment with the purchases that could add an installment to the invoice.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CardInvoicePaymentService {

    private final CardInvoiceRepository invoiceRepository;
    private final CardInvoicePaymentRepository paymentRepository;
    private final CardInstallmentRepository installmentRepository;
    private final MovementSettlementRepository settlementRepository;
    private final CreditCardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final MovementSettlementService settlementService;
    private final CardInvoicePaymentMapper mapper;
    private final PayCycleMovementGuard payCycleMovementGuard;
    private final Clock clock;

    /**
     * Pays the invoice in full. 409 when it already has an active payment or has nothing left to
     * pay; 400 for a missing date or an unusable account.
     */
    @Transactional
    public CardInvoicePayment pay(final UUID invoiceCode, final UUID accountCode, final LocalDate paymentDate) {
        log.info("m=pay, invoiceCode={}, accountCode={}, paymentDate={}", invoiceCode, accountCode, paymentDate);
        if (paymentDate == null) {
            throw new InvalidFieldsException("Campos inválidos", CardInvoicePayment.class,
                    Map.of("paymentDate", "deve ser informada"));
        }
        payCycleMovementGuard.lock();
        final CardInvoiceEntity invoice = findInvoice(invoiceCode);
        final CreditCardEntity card = lockCard(invoice);
        if (paymentRepository.existsActiveByInvoiceId(invoice.getId())) {
            throw new ConflictException("A fatura já foi paga");
        }
        final List<FinancialMovementEntity> installments =
                installmentRepository.findForUpdateActiveByInvoiceId(invoice.getId());
        final BigDecimal remaining = installments.stream()
                .map(this::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.signum() <= 0) {
            throw new ConflictException("A fatura não tem valor a pagar");
        }
        final AccountEntity account = resolveAccount(accountCode, card, paymentDate);

        final CardInvoicePaymentEntity payment = paymentRepository.save(CardInvoicePaymentEntity.builder()
                .invoice(invoice)
                .account(account)
                .paymentDate(paymentDate)
                .amount(remaining)
                .build());
        installments.forEach(installment ->
                settlementService.settleRemainingForInvoicePayment(installment, account, paymentDate, payment));
        return mapper.toModel(payment);
    }

    /** The payments of the invoice, newest first, reversed ones included. */
    @Transactional(readOnly = true)
    public List<CardInvoicePayment> findAll(final UUID invoiceCode) {
        log.info("m=findAll, invoiceCode={}", invoiceCode);
        final CardInvoiceEntity invoice = findInvoice(invoiceCode);
        return mapper.toModelList(paymentRepository.findAllByInvoiceId(invoice.getId()));
    }

    /**
     * Reverses the payment as a whole: every settlement it generated is reversed, the installments
     * go back to what they owed before (PENDING or LATE) and the limit is released again.
     */
    @Transactional
    public CardInvoicePayment reverse(final UUID invoiceCode, final UUID paymentCode) {
        log.info("m=reverse, invoiceCode={}, paymentCode={}", invoiceCode, paymentCode);
        payCycleMovementGuard.lock();
        final CardInvoiceEntity invoice = findInvoice(invoiceCode);
        lockCard(invoice);
        final CardInvoicePaymentEntity payment = paymentRepository
                .findForUpdateByCodeAndInvoiceId(paymentCode, invoice.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CardInvoicePaymentEntity not found for code " + paymentCode));
        if (payment.getReversedAt() != null) {
            throw new ConflictException("O pagamento da fatura já foi estornado");
        }
        installmentRepository.findForUpdateActiveByInvoiceId(invoice.getId());
        final List<MovementSettlementEntity> settlements =
                settlementRepository.findActiveByInvoicePaymentId(payment.getId());
        settlements.forEach(settlementService::reverseForInvoicePayment);
        payment.setReversedAt(LocalDateTime.now(clock));
        paymentRepository.flush();
        return mapper.toModel(payment);
    }

    private CardInvoiceEntity findInvoice(final UUID invoiceCode) {
        return invoiceRepository.findByCode(invoiceCode)
                .orElseThrow(() -> new EntityNotFoundException("CardInvoiceEntity not found for code " + invoiceCode));
    }

    private CreditCardEntity lockCard(final CardInvoiceEntity invoice) {
        return cardRepository.findForUpdateByCode(invoice.getCard().getCode())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CreditCardEntity not found for code " + invoice.getCard().getCode()));
    }

    private BigDecimal remaining(final FinancialMovementEntity installment) {
        final BigDecimal paid = installment.getAmountPaid() == null
                ? BigDecimal.ZERO : installment.getAmountPaid().abs();
        return installment.getAmount().abs().subtract(paid);
    }

    /**
     * The informed account; otherwise the card's default account; otherwise the default account.
     * It must be ACTIVE and already exist on the payment date.
     */
    private AccountEntity resolveAccount(final UUID accountCode, final CreditCardEntity card,
                                         final LocalDate paymentDate) {
        final AccountEntity account;
        if (accountCode != null) {
            account = accountRepository.findByCode(accountCode).orElseThrow(() -> new InvalidFieldsException(
                    "Campos inválidos", CardInvoicePayment.class, Map.of("accountCode", "conta não encontrada")));
        } else if (card.getDefaultAccount() != null) {
            account = card.getDefaultAccount();
        } else {
            account = accountRepository.findByDefaultAccountTrue()
                    .orElseThrow(() -> new IllegalStateException("No default account configured"));
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidFieldsException("Campos inválidos", CardInvoicePayment.class,
                    Map.of("accountCode", "conta inativa"));
        }
        if (paymentDate.isBefore(account.getInitialBalanceDate())) {
            throw new InvalidFieldsException("Campos inválidos", CardInvoicePayment.class,
                    Map.of("paymentDate", "não pode ser anterior à data do saldo inicial da conta ("
                            + account.getInitialBalanceDate() + ")"));
        }
        return account;
    }
}
