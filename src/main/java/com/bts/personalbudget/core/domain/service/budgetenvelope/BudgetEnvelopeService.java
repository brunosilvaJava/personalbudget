package com.bts.personalbudget.core.domain.service.budgetenvelope;

import com.bts.personalbudget.core.domain.entity.BudgetEnvelopeEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.BudgetEnvelopeMapper;
import com.bts.personalbudget.repository.BudgetEnvelopeRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envelopes per cycle (E10). The plan is one amount per (cycle, DEBIT category); what the cycle
 * did with it comes from the cycle summary (E5), the same numbers the cycle report shows, so an
 * envelope and the report never disagree: recognized = paid, committed = still to pay.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetEnvelopeService {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final BudgetEnvelopeRepository repository;
    private final PayCycleRepository cycleRepository;
    private final PayCycleService payCycleService;
    private final PayCycleSummaryService summaryService;
    private final CategoryService categoryService;
    private final BudgetEnvelopeMapper mapper;

    /**
     * 400 for a bad amount or a category that is unknown, inactive or not DEBIT; 404 for an
     * unknown cycle; 409 when the cycle is closed or cancelled or the category already has an
     * active envelope in it.
     */
    @Transactional
    public BudgetEnvelope create(final UUID cycleCode, final UUID categoryCode, final BigDecimal plannedAmount) {
        log.info("m=create, cycleCode={}, categoryCode={}, plannedAmount={}", cycleCode, categoryCode, plannedAmount);
        validateAmount(plannedAmount);
        if (categoryCode == null) {
            throw new InvalidFieldsException("Campos inválidos", BudgetEnvelope.class,
                    Map.of("categoryCode", "deve ser informada"));
        }
        categoryService.validateForUse(categoryCode, OperationType.DEBIT);
        requireOpenCycle(cycleCode);
        final PayCycleEntity cycle = cycleRepository.findByCode(cycleCode)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + cycleCode));
        if (repository.existsActiveByCycleIdAndCategoryCode(cycle.getId(), categoryCode)) {
            throw new ConflictException("A categoria já tem um envelope neste ciclo");
        }
        final BudgetEnvelopeEntity saved;
        try {
            saved = repository.saveAndFlush(BudgetEnvelopeEntity.builder()
                    .cycle(cycle)
                    .categoryCode(categoryCode)
                    .plannedAmount(plannedAmount)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A categoria já tem um envelope neste ciclo");
        }
        return describe(List.of(saved), cycleCode).envelopes().get(0);
    }

    /** The active envelopes of the cycle, with what was consumed, and their totals. */
    @Transactional(readOnly = true)
    public CycleEnvelopes find(final UUID cycleCode) {
        log.info("m=find, cycleCode={}", cycleCode);
        final PayCycleEntity cycle = cycleRepository.findByCode(cycleCode)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + cycleCode));
        return describe(repository.findActiveByCycleId(cycle.getId()), cycleCode);
    }

    @Transactional
    public BudgetEnvelope update(final UUID code, final BigDecimal plannedAmount) {
        log.info("m=update, code={}, plannedAmount={}", code, plannedAmount);
        validateAmount(plannedAmount);
        final BudgetEnvelopeEntity envelope = findActive(code);
        requireOpenCycle(envelope.getCycle().getCode());
        envelope.setPlannedAmount(plannedAmount);
        repository.saveAndFlush(envelope);
        return describe(List.of(envelope), envelope.getCycle().getCode()).envelopes().get(0);
    }

    /** Logical exclusion: the category can get a new envelope in the cycle afterwards. */
    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete, code={}", code);
        final BudgetEnvelopeEntity envelope = repository.findActiveByCode(code).orElse(null);
        if (envelope == null) {
            return;
        }
        requireOpenCycle(envelope.getCycle().getCode());
        envelope.delete();
        repository.save(envelope);
    }

    private BudgetEnvelopeEntity findActive(final UUID code) {
        return repository.findActiveByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("BudgetEnvelopeEntity not found for code " + code));
    }

    private void requireOpenCycle(final UUID cycleCode) {
        final PayCycleStatus status = payCycleService.findByCode(cycleCode).status();
        if (status == PayCycleStatus.CLOSED || status == PayCycleStatus.CANCELLED) {
            throw new ConflictException("O ciclo está " + (status == PayCycleStatus.CLOSED ? "fechado" : "cancelado")
                    + "; seus envelopes não podem ser alterados");
        }
    }

    private void validateAmount(final BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2) {
            throw new InvalidFieldsException("Campos inválidos", BudgetEnvelope.class,
                    Map.of("plannedAmount", "deve ser maior que zero, com até 2 casas decimais"));
        }
    }

    private CycleEnvelopes describe(final List<BudgetEnvelopeEntity> entities, final UUID cycleCode) {
        final PayCycleSummary summary = summaryService.summarize(cycleCode);
        final Map<UUID, PayCycleSummary.CategoryDetail> expenses = summary.categories().stream()
                .filter(category -> category.operationType() == OperationType.DEBIT && category.categoryCode() != null)
                .collect(Collectors.toMap(PayCycleSummary.CategoryDetail::categoryCode, Function.identity()));
        final Map<UUID, String> names = categoryService.findAllByCodes(
                        entities.stream().map(BudgetEnvelopeEntity::getCategoryCode).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(category -> category.code(), category -> category.name()));
        final List<BudgetEnvelope> envelopes = entities.stream()
                .map(entity -> mapper.assemble(entity, names.get(entity.getCategoryCode()),
                        expenses.get(entity.getCategoryCode())))
                .toList();
        return new CycleEnvelopes(cycleCode, envelopes,
                sum(envelopes, BudgetEnvelope::plannedAmount),
                sum(envelopes, BudgetEnvelope::consumedAmount),
                sum(envelopes, BudgetEnvelope::reservedAmount),
                sum(envelopes, BudgetEnvelope::overrunAmount),
                summary.projectionIncomplete());
    }

    private static BigDecimal sum(final List<BudgetEnvelope> envelopes,
                                  final Function<BudgetEnvelope, BigDecimal> value) {
        return envelopes.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
