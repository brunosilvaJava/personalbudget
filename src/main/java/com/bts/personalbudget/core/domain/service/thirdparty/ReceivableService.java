package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.ReceivableStatus;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReceivableService {
    private final FinancialMovementRepository movementRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CardPurchaseRepository purchaseRepository;
    private final CardInvoiceRepository invoiceRepository;
    private final MovementSettlementService settlementService;
    private final PayCycleMovementGuard payCycleMovementGuard;
    private final Clock clock;

    /** Creates or brings in sync the one receivable owned by a third-party card installment. */
    @Transactional
    public void syncForInstallment(final FinancialMovementEntity source, final LocalDate previousDueDate) {
        log.info("m=syncForInstallment, source={}", source.getCode());
        if (source.getThirdParty() == null) {
            movementRepository.findActiveReceivableBySourceMovementId(source.getId()).ifPresent(receivable -> {
                requireNotReceived(receivable);
                receivable.delete();
            });
            return;
        }
        final FinancialMovementEntity receivable = movementRepository
                .findActiveReceivableBySourceMovementId(source.getId()).orElseGet(() -> newReceivable(source));
        if (receivable.getId() != null) {
            requireNotReceived(receivable);
            receivable.setThirdParty(source.getThirdParty());
            receivable.setDescription(receivableDescription(source));
            receivable.setAmount(source.getAmount().abs());
            if (previousDueDate != null && receivable.getDueDate().toLocalDate().equals(previousDueDate)) {
                receivable.setDueDate(source.getDueDate());
            }
            receivable.setMovementDate(source.getMovementDate());
            receivable.setCompetenceDate(source.getCompetenceDate());
            receivable.setStatus(movementStatusFor(receivable));
            movementRepository.save(receivable);
        } else {
            movementRepository.save(receivable);
        }
    }

    @Transactional
    public void syncForInstallment(final FinancialMovementEntity source) {
        syncForInstallment(source, null);
    }

    @Transactional
    public void requireNoReceived(final Collection<FinancialMovementEntity> sources) {
        log.info("m=requireNoReceived, sources={}", sources.size());
        for (final FinancialMovementEntity source : sources) {
            movementRepository.findActiveReceivableBySourceMovementId(source.getId())
                    .ifPresent(ReceivableService::requireNotReceived);
        }
    }

    @Transactional
    public void deleteForInstallments(final Collection<FinancialMovementEntity> sources) {
        log.info("m=deleteForInstallments, sources={}", sources.size());
        for (final FinancialMovementEntity source : sources) {
            movementRepository.findActiveReceivableBySourceMovementId(source.getId()).ifPresent(receivable -> {
                requireNotReceived(receivable);
                receivable.delete();
            });
        }
    }

    @Transactional(readOnly = true)
    public List<Receivable> find(final UUID thirdPartyCode) {
        log.info("m=find, thirdPartyCode={}", thirdPartyCode);
        final ThirdPartyEntity thirdParty = thirdParty(thirdPartyCode);
        return movementRepository.findActiveReceivablesByThirdPartyId(thirdParty.getId()).stream().map(this::describe).toList();
    }

    @Transactional
    public Receivable updateDueDate(final UUID thirdPartyCode, final UUID receivableCode, final LocalDate dueDate) {
        log.info("m=updateDueDate, thirdPartyCode={}, receivableCode={}, dueDate={}", thirdPartyCode, receivableCode, dueDate);
        if (dueDate == null) throw invalid("dueDate", "deve ser informada");
        payCycleMovementGuard.lock();
        final FinancialMovementEntity receivable = receivable(thirdPartyCode, receivableCode);
        payCycleMovementGuard.requireEditable(receivable.getCompetenceDate());
        receivable.setDueDate(dueDate.atStartOfDay());
        receivable.setStatus(movementStatusFor(receivable));
        return describe(movementRepository.save(receivable));
    }

    /** Allocates one cash receipt across the oldest open receivables, without reopening closed cycles. */
    @Transactional
    public List<Receivable> receive(final UUID thirdPartyCode, final BigDecimal amount, final LocalDate effectiveDate,
                                    final UUID accountCode) {
        log.info("m=receive, thirdPartyCode={}, amount={}, effectiveDate={}, accountCode={}",
                thirdPartyCode, amount, effectiveDate, accountCode);
        if (amount == null || amount.signum() <= 0) throw invalid("amount", "deve ser maior que zero");
        if (effectiveDate == null) throw invalid("effectiveDate", "deve ser informada");
        final ThirdPartyEntity thirdParty = thirdParty(thirdPartyCode);
        final List<FinancialMovementEntity> receivables = movementRepository
                .findActiveReceivablesForUpdateByThirdPartyId(thirdParty.getId());
        final BigDecimal open = receivables.stream().map(this::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(open) > 0) throw new ConflictException("Recebimento maior que o saldo em aberto");
        BigDecimal left = amount;
        for (final FinancialMovementEntity receivable : receivables) {
            final BigDecimal current = remaining(receivable);
            if (left.signum() == 0) break;
            if (current.signum() <= 0) continue;
            final BigDecimal allocation = left.min(current);
            settlementService.create(receivable.getCode(), new MovementSettlement(null, receivable.getCode(), allocation,
                    effectiveDate, null, null, OperationType.CREDIT, null, receivable.getCompetenceDate(), accountCode, true));
            left = left.subtract(allocation);
        }
        return movementRepository.findActiveReceivablesByThirdPartyId(thirdParty.getId()).stream().map(this::describe).toList();
    }

    @Transactional
    public Receivable writeOff(final UUID thirdPartyCode, final UUID receivableCode, final LocalDate effectiveDate,
                                final BigDecimal amount) {
        log.info("m=writeOff, thirdPartyCode={}, receivableCode={}, effectiveDate={}, amount={}",
                thirdPartyCode, receivableCode, effectiveDate, amount);
        if (effectiveDate == null) throw invalid("effectiveDate", "deve ser informada");
        payCycleMovementGuard.lock();
        final FinancialMovementEntity receivable = receivable(thirdPartyCode, receivableCode);
        payCycleMovementGuard.requireEditable(effectiveDate);
        final BigDecimal remaining = remaining(receivable);
        final BigDecimal applied = amount == null ? remaining : amount;
        if (applied.signum() <= 0 || applied.compareTo(remaining) > 0) {
            throw invalid("amount", "deve estar entre zero e o saldo em aberto");
        }
        final BigDecimal written = zero(receivable.getWrittenOffAmount()).add(applied);
        receivable.setWrittenOffAmount(written);
        receivable.setWrittenOffDate(effectiveDate);
        return describe(movementRepository.save(receivable));
    }

    private FinancialMovementEntity newReceivable(final FinancialMovementEntity source) {
        return FinancialMovementEntity.builder()
                .operationType(OperationType.CREDIT)
                .description(receivableDescription(source))
                .amount(source.getAmount().abs())
                .amountPaid(BigDecimal.ZERO)
                .movementDate(source.getMovementDate())
                .dueDate(source.getDueDate())
                .competenceDate(source.getCompetenceDate())
                .competenceOverridden(true)
                .status(source.getDueDate().toLocalDate().isBefore(LocalDate.now(clock))
                        ? FinancialMovementStatus.LATE : FinancialMovementStatus.PENDING)
                .thirdParty(source.getThirdParty())
                .receivableSourceMovementId(source.getId())
                .build();
    }

    private Receivable describe(final FinancialMovementEntity receivable) {
        final FinancialMovementEntity source = movementRepository.findById(receivable.getReceivableSourceMovementId())
                .orElseThrow(() -> new IllegalStateException("Fonte do recebível não encontrada"));
        final UUID purchaseCode = source.getCardPurchaseId() == null ? null : purchaseRepository.findById(source.getCardPurchaseId())
                .map(purchase -> purchase.getCode()).orElse(null);
        final UUID invoiceCode = source.getCardInvoiceId() == null ? null : invoiceRepository.findById(source.getCardInvoiceId())
                .map(invoice -> invoice.getCode()).orElse(null);
        final BigDecimal received = zero(receivable.getAmountPaid()).abs();
        final BigDecimal written = zero(receivable.getWrittenOffAmount());
        return new Receivable(receivable.getCode(), receivable.getThirdParty().getCode(), statusFor(receivable),
                receivable.getAmount().abs(), received, written, remaining(receivable), receivable.getDueDate().toLocalDate(),
                purchaseCode, source.getInstallmentNumber(), invoiceCode, source.getDescription());
    }

    private FinancialMovementEntity receivable(final UUID thirdPartyCode, final UUID receivableCode) {
        final FinancialMovementEntity entity = movementRepository.findForUpdateByCodeAndFlagActiveTrue(receivableCode)
                .orElseThrow(() -> new EntityNotFoundException("Receivable not found for code " + receivableCode));
        if (entity.getReceivableSourceMovementId() == null || entity.getThirdParty() == null
                || !entity.getThirdParty().getCode().equals(thirdPartyCode)) {
            throw new EntityNotFoundException("Receivable not found for code " + receivableCode);
        }
        return entity;
    }

    private ThirdPartyEntity thirdParty(final UUID code) {
        return thirdPartyRepository.findByCode(code).orElseThrow(() -> new EntityNotFoundException(
                "ThirdPartyEntity not found for code " + code));
    }

    private ReceivableStatus statusFor(final FinancialMovementEntity receivable) {
        if (remaining(receivable).signum() == 0 && zero(receivable.getWrittenOffAmount()).signum() > 0) return ReceivableStatus.WRITTEN_OFF;
        if (remaining(receivable).signum() == 0) return ReceivableStatus.PAID_OUT;
        return receivable.getDueDate().toLocalDate().isBefore(LocalDate.now(clock)) ? ReceivableStatus.LATE : ReceivableStatus.PENDING;
    }

    private FinancialMovementStatus movementStatusFor(final FinancialMovementEntity receivable) {
        return receivable.getDueDate().toLocalDate().isBefore(LocalDate.now(clock))
                ? FinancialMovementStatus.LATE : FinancialMovementStatus.PENDING;
    }

    private BigDecimal remaining(final FinancialMovementEntity entity) {
        return entity.getAmount().abs().subtract(zero(entity.getAmountPaid()).abs()).subtract(zero(entity.getWrittenOffAmount()));
    }
    private static BigDecimal zero(final BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static void requireNotReceived(final FinancialMovementEntity receivable) {
        if (zero(receivable.getAmountPaid()).signum() != 0) {
            throw new ConflictException("Há valores já recebidos; estorne os recebimentos antes");
        }
    }
    private static String receivableDescription(final FinancialMovementEntity source) {
        final String description = "A receber: " + source.getDescription();
        return description.length() <= 120 ? description : description.substring(0, 120);
    }
    private static InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException("Campos inválidos", ReceivableService.class, Map.of(field, message));
    }
}
