package com.bts.personalbudget.core.domain.service.movementsettlement;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.MovementSettlementMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.FinancialMovementRepository;
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

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;

@Slf4j
@RequiredArgsConstructor
@Service
public class MovementSettlementService {

    private final FinancialMovementRepository movementRepository;
    private final MovementSettlementRepository settlementRepository;
    private final AccountRepository accountRepository;
    private final MovementSettlementMapper mapper;
    private final PayCycleMovementGuard payCycleMovementGuard;
    private final Clock clock;

    @Transactional
    public MovementSettlement create(final UUID movementCode, final MovementSettlement settlement) {
        log.info("m=create, movementCode={}, settlement={}", movementCode, settlement);
        validateAmount(settlement.amount());
        if (settlement.effectiveDate() == null) {
            throw new InvalidFieldsException("Campos inválidos", MovementSettlement.class,
                    Map.of("effectiveDate", "deve ser informada"));
        }
        payCycleMovementGuard.lock();
        final FinancialMovementEntity movement = findMovementForUpdate(movementCode);
        if (movement.getCardPurchaseId() != null) {
            throw new ConflictException(
                    "Esta movimentação é a parcela de uma compra de cartão; pague a fatura");
        }
        final AccountEntity account = resolveAccount(settlement.accountCode(), settlement.effectiveDate());
        final BigDecimal paidBefore = paidAmount(movement);
        final BigDecimal total = movement.getAmount().abs();
        if (paidBefore.add(settlement.amount()).compareTo(total) > 0) {
            throw new ConflictException("O pagamento excede o valor restante da movimentação");
        }

        final MovementSettlementEntity entity = mapper.toEntity(settlement);
        entity.setFinancialMovement(movement);
        entity.setAccount(account);
        entity.setAmount(settlement.amount());
        entity.setEffectiveDate(settlement.effectiveDate());
        final MovementSettlementEntity saved = settlementRepository.save(entity);
        updateMovementSummary(movement, paidBefore.add(settlement.amount()));
        return mapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public List<MovementSettlement> findAll(final UUID movementCode) {
        log.info("m=findAll, movementCode={}", movementCode);
        findMovement(movementCode);
        return mapper.toModelList(settlementRepository.findAllByMovementCode(movementCode));
    }

    @Transactional
    public MovementSettlement reverse(final UUID movementCode, final UUID settlementCode) {
        log.info("m=reverse, movementCode={}, settlementCode={}", movementCode, settlementCode);
        payCycleMovementGuard.lock();
        final FinancialMovementEntity movement = findMovementForUpdate(movementCode);
        final MovementSettlementEntity settlement = settlementRepository
                .findForUpdateByCodeAndMovementId(settlementCode, movement.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "MovementSettlementEntity not found for code " + settlementCode));
        if (settlement.getReversedAt() != null) {
            throw new ConflictException("A liquidação já foi estornada");
        }
        if (settlement.getInvoicePayment() != null) {
            throw new ConflictException(
                    "Esta liquidação veio do pagamento de uma fatura; estorne o pagamento da fatura");
        }

        final BigDecimal paidBefore = paidAmount(movement);
        final BigDecimal paidAfter = paidBefore.subtract(settlement.getAmount());
        if (paidAfter.signum() < 0) {
            throw new IllegalStateException("Settlement total is inconsistent with movement summary");
        }
        settlement.reverse(LocalDateTime.now(clock));
        updateMovementSummary(movement, paidAfter);
        settlementRepository.flush();
        return mapper.toModel(settlement);
    }

    /**
     * Settles what is still owed on an installment as part of an invoice payment. The caller holds
     * the lock on the movement and has validated the account and the date. Nothing is recorded
     * (and null is returned) when the movement has nothing left to pay.
     */
    @Transactional
    public MovementSettlementEntity settleRemainingForInvoicePayment(
            final FinancialMovementEntity movement, final AccountEntity account,
            final LocalDate effectiveDate, final CardInvoicePaymentEntity payment) {
        log.info("m=settleRemainingForInvoicePayment, movementCode={}, paymentCode={}",
                movement.getCode(), payment.getCode());
        final BigDecimal total = movement.getAmount().abs();
        final BigDecimal paidBefore = paidAmount(movement);
        final BigDecimal remaining = total.subtract(paidBefore);
        if (remaining.signum() <= 0) {
            return null;
        }
        final MovementSettlementEntity saved = settlementRepository.save(MovementSettlementEntity.builder()
                .financialMovement(movement)
                .account(account)
                .invoicePayment(payment)
                .amount(remaining)
                .effectiveDate(effectiveDate)
                .build());
        updateMovementSummary(movement, total);
        return saved;
    }

    /** Reverses a settlement generated by an invoice payment; the caller holds the movement lock. */
    @Transactional
    public void reverseForInvoicePayment(final MovementSettlementEntity settlement) {
        log.info("m=reverseForInvoicePayment, settlementCode={}", settlement.getCode());
        final FinancialMovementEntity movement = settlement.getFinancialMovement();
        final BigDecimal paidAfter = paidAmount(movement).subtract(settlement.getAmount());
        if (paidAfter.signum() < 0) {
            throw new IllegalStateException("Settlement total is inconsistent with movement summary");
        }
        settlement.reverse(LocalDateTime.now(clock));
        updateMovementSummary(movement, paidAfter);
    }

    @Transactional(readOnly = true)
    public List<MovementSettlement> findForCashDateBetween(final LocalDate startDate, final LocalDate endDate) {
        log.info("m=findForCashDateBetween, startDate={}, endDate={}", startDate, endDate);
        return mapper.toModelList(settlementRepository.findActiveForCashDateBetween(startDate, endDate));
    }

    @Transactional(readOnly = true)
    public List<MovementSettlement> findByMovementCompetenceInterval(
            final LocalDate from, final LocalDate toExclusive) {
        log.info("m=findByMovementCompetenceInterval, from={}, toExclusive={}", from, toExclusive);
        return mapper.toModelList(
                settlementRepository.findActiveByMovementCompetenceInterval(from, toExclusive));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumActiveSignedAmountsThrough(final LocalDate date) {
        log.info("m=sumActiveSignedAmountsThrough, date={}", date);
        return settlementRepository.sumActiveSignedAmountsThrough(date, OperationType.DEBIT);
    }

    private FinancialMovementEntity findMovement(final UUID movementCode) {
        return movementRepository.findByCodeAndFlagActiveTrue(movementCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "FinancialMovementEntity not found for code " + movementCode));
    }

    private FinancialMovementEntity findMovementForUpdate(final UUID movementCode) {
        return movementRepository.findForUpdateByCodeAndFlagActiveTrue(movementCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "FinancialMovementEntity not found for code " + movementCode));
    }

    /**
     * The informed account, or the default one when none is informed. It must exist, be ACTIVE and
     * already exist on the effective date (not before its initial balance date).
     */
    private AccountEntity resolveAccount(final UUID accountCode, final LocalDate effectiveDate) {
        final AccountEntity account = accountCode == null
                ? accountRepository.findByDefaultAccountTrue().orElseThrow(
                        () -> new IllegalStateException("No default account configured"))
                : accountRepository.findByCode(accountCode).orElseThrow(() -> new InvalidFieldsException(
                        "Campos inválidos", MovementSettlement.class, Map.of("accountCode", "conta não encontrada")));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidFieldsException("Campos inválidos", MovementSettlement.class,
                    Map.of("accountCode", "conta inativa"));
        }
        if (effectiveDate.isBefore(account.getInitialBalanceDate())) {
            throw new InvalidFieldsException("Campos inválidos", MovementSettlement.class,
                    Map.of("effectiveDate", "não pode ser anterior à data do saldo inicial da conta ("
                            + account.getInitialBalanceDate() + ")"));
        }
        return account;
    }

    private void validateAmount(final BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidFieldsException("Campos inválidos", MovementSettlement.class,
                    Map.of("amount", "deve ser maior que zero"));
        }
    }

    private BigDecimal paidAmount(final FinancialMovementEntity movement) {
        return movement.getAmountPaid() == null ? BigDecimal.ZERO : movement.getAmountPaid().abs();
    }

    private void updateMovementSummary(final FinancialMovementEntity movement, final BigDecimal paidAmount) {
        final BigDecimal normalizedAmount = movement.getOperationType() == OperationType.DEBIT
                ? paidAmount.negate() : paidAmount;
        movement.setAmountPaid(normalizedAmount);
        movement.setPayDate(null);
        final BigDecimal total = movement.getAmount().abs();
        if (paidAmount.compareTo(total) == 0) {
            movement.setStatus(PAID_OUT);
            return;
        }
        movement.setStatus(movement.getDueDate().toLocalDate().isBefore(LocalDate.now(clock)) ? LATE : PENDING);
    }
}
