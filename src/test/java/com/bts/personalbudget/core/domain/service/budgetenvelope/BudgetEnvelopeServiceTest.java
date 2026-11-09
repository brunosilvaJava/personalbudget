package com.bts.personalbudget.core.domain.service.budgetenvelope;

import com.bts.personalbudget.core.domain.entity.BudgetEnvelopeEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.factory.BudgetEnvelopeFactory;
import com.bts.personalbudget.core.domain.factory.CategoryFactory;
import com.bts.personalbudget.core.domain.factory.PayCycleFactory;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.BudgetEnvelopeMapper;
import com.bts.personalbudget.repository.BudgetEnvelopeRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetEnvelopeServiceTest {

    private static final UUID CYCLE = BudgetEnvelopeFactory.CYCLE_CODE;
    private static final UUID CATEGORY = CategoryFactory.CATEGORY_CODE;
    private static final BigDecimal PLANNED = new BigDecimal("500.00");

    @InjectMocks
    private BudgetEnvelopeService service;

    @Mock
    private BudgetEnvelopeRepository repository;

    @Mock
    private PayCycleRepository cycleRepository;

    @Mock
    private PayCycleService payCycleService;

    @Mock
    private PayCycleSummaryService summaryService;

    @Mock
    private CategoryService categoryService;

    @Spy
    private BudgetEnvelopeMapper mapper = Mappers.getMapper(BudgetEnvelopeMapper.class);

    private final BudgetEnvelopeEntity entity = BudgetEnvelopeFactory.buildEntity();

    private void givenCycle(final PayCycleStatus status) {
        final PayCycleView view = new PayCycleView(PayCycleFactory.buildTransition(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), status, List.of());
        when(payCycleService.findByCode(CYCLE)).thenReturn(view);
        when(cycleRepository.findByCode(CYCLE)).thenReturn(Optional.of(entity.getCycle()));
        when(summaryService.summarize(CYCLE)).thenReturn(new PayCycleSummary(view, BigDecimal.ZERO,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("300.00"),
                List.of(new PayCycleSummary.CategoryDetail(CATEGORY, CategoryFactory.CATEGORY_NAME,
                        OperationType.DEBIT, new BigDecimal("100.00"), new BigDecimal("300.00"))), List.of()));
        when(categoryService.findAllByCodes(any())).thenReturn(List.of(new Category(CATEGORY,
                CategoryFactory.CATEGORY_NAME, OperationType.DEBIT, null, null, null)));
        when(repository.saveAndFlush(any(BudgetEnvelopeEntity.class))).thenAnswer(invocation -> {
            final BudgetEnvelopeEntity saved = invocation.getArgument(0);
            saved.setId(40L);
            saved.prePersist();
            return saved;
        });
    }

    @Test
    void shouldCreateAnEnvelopeAndDescribeItWithTheCycleConsumption() {
        givenCycle(PayCycleStatus.OPEN);

        final BudgetEnvelope created = service.create(CYCLE, CATEGORY, PLANNED);

        assertThat(created.plannedAmount()).isEqualByComparingTo("500.00");
        assertThat(created.recognizedAmount()).isEqualByComparingTo("100.00");
        assertThat(created.committedAmount()).isEqualByComparingTo("200.00");
        assertThat(created.reservedAmount()).isEqualByComparingTo("200.00");
        assertThat(created.categoryName()).isEqualTo(CategoryFactory.CATEGORY_NAME);
        verify(categoryService).validateForUse(CATEGORY, OperationType.DEBIT);
    }

    @Test
    void shouldRejectAnInvalidAmountOrAMissingCategoryBeforeAnyLookup() {
        givenCycle(PayCycleStatus.OPEN);

        for (final BigDecimal amount : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1.00"),
                new BigDecimal("10.001"), new BigDecimal("10000000000.00")}) {
            assertThrows(InvalidFieldsException.class, () -> service.create(CYCLE, CATEGORY, amount));
        }
        assertThrows(InvalidFieldsException.class, () -> service.create(CYCLE, null, PLANNED));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseASecondActiveEnvelopeForTheSameCategoryInTheCycle() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.existsActiveByCycleIdAndCategoryCode(entity.getCycle().getId(), CATEGORY)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(CYCLE, CATEGORY, PLANNED));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTurnAUniqueKeyRaceIntoAConflict() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.saveAndFlush(any(BudgetEnvelopeEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(ConflictException.class, () -> service.create(CYCLE, CATEGORY, PLANNED));
    }

    @Test
    void shouldNotChangeEnvelopesOfAClosedOrCancelledCycle() {
        when(repository.findActiveByCode(entity.getCode())).thenReturn(Optional.of(entity));
        for (final PayCycleStatus status : new PayCycleStatus[]{PayCycleStatus.CLOSED, PayCycleStatus.CANCELLED}) {
            givenCycle(status);

            assertThrows(ConflictException.class, () -> service.create(CYCLE, CATEGORY, PLANNED));
            assertThrows(ConflictException.class, () -> service.update(entity.getCode(), PLANNED));
            assertThrows(ConflictException.class, () -> service.delete(entity.getCode()));
        }
        assertThat(entity.isFlagActive()).isTrue();
    }

    @Test
    void shouldRejectCreatingInAnUnknownCycle() {
        when(payCycleService.findByCode(CYCLE)).thenThrow(new EntityNotFoundException("not found"));

        assertThrows(EntityNotFoundException.class, () -> service.create(CYCLE, CATEGORY, PLANNED));
    }

    @Test
    void shouldListTheActiveEnvelopesWithTotals() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.findActiveByCycleId(entity.getCycle().getId())).thenReturn(List.of(entity));

        final CycleEnvelopes envelopes = service.find(CYCLE);

        assertThat(envelopes.envelopes()).hasSize(1);
        assertThat(envelopes.totalPlanned()).isEqualByComparingTo("500.00");
        assertThat(envelopes.totalConsumed()).isEqualByComparingTo("300.00");
        assertThat(envelopes.totalReserved()).isEqualByComparingTo("200.00");
        assertThat(envelopes.totalOverrun()).isEqualByComparingTo("0");
        assertThat(envelopes.projectionIncomplete()).isFalse();
    }

    @Test
    void shouldListAnEmptyCycleWithZeroTotals() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.findActiveByCycleId(entity.getCycle().getId())).thenReturn(List.of());

        final CycleEnvelopes envelopes = service.find(CYCLE);

        assertThat(envelopes.envelopes()).isEmpty();
        assertThat(envelopes.totalPlanned()).isEqualByComparingTo("0");
    }

    @Test
    void shouldUpdateThePlannedAmountAndReturnNotFoundForAnUnknownEnvelope() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.findActiveByCode(entity.getCode())).thenReturn(Optional.of(entity));

        final BudgetEnvelope updated = service.update(entity.getCode(), new BigDecimal("250.00"));

        assertThat(updated.plannedAmount()).isEqualByComparingTo("250.00");
        assertThat(updated.overrunAmount()).isEqualByComparingTo("50.00");
        assertThrows(EntityNotFoundException.class, () -> service.update(UUID.randomUUID(), PLANNED));
    }

    @Test
    void shouldDeleteLogicallyAndTreatAnUnknownEnvelopeAsAlreadyDeleted() {
        givenCycle(PayCycleStatus.OPEN);
        when(repository.findActiveByCode(entity.getCode())).thenReturn(Optional.of(entity));

        service.delete(entity.getCode());
        service.delete(UUID.randomUUID());

        assertThat(entity.isFlagActive()).isFalse();
        verify(repository).save(entity);
    }
}
