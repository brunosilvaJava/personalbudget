package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.TRANSITION;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.CLOSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotCategoryEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotEntity;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.PayCycleSnapshotMapper;
import com.bts.personalbudget.repository.PayCycleRepository;
import com.bts.personalbudget.repository.PayCycleSnapshotCategoryRepository;
import com.bts.personalbudget.repository.PayCycleSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayCycleSnapshotServiceTest {
    private static final UUID CODE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CATEGORY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);

    @Mock private PayCycleConfigService configService;
    @Mock private PayCycleRepository cycleRepository;
    @Mock private PayCycleSnapshotRepository snapshotRepository;
    @Mock private PayCycleSnapshotCategoryRepository categoryRepository;
    @Spy private PayCycleSnapshotMapper mapper = PayCycleSnapshotMapper.INSTANCE;
    @InjectMocks private PayCycleSnapshotService service;

    @Test
    void shouldPreserveCategoryNamesAndKeysWhenRecordingClosure() {
        final PayCycleEntity cycle = entity();
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(cycle));
        when(snapshotRepository.findFirstByPayCycleIdOrderBySnapshotVersionDesc(1L))
                .thenReturn(Optional.of(PayCycleSnapshotEntity.builder().snapshotVersion(2).build()));
        when(snapshotRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findAllBySnapshotIdOrderByIdAsc(null)).thenReturn(List.of());

        final PayCycleSnapshot result = service.recordClosure(summary(List.of()));

        assertThat(result.snapshotVersion()).isEqualTo(3);
        assertThat(result.recognizedExpense()).isEqualByComparingTo("15.00");
        final ArgumentCaptor<List<PayCycleSnapshotCategoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(PayCycleSnapshotCategoryEntity::getCategoryKey)
                .containsExactly(CATEGORY.toString(), "UNCATEGORIZED");
        assertThat(captor.getValue()).extracting(PayCycleSnapshotCategoryEntity::getCategoryName)
                .containsExactly("Casa", "Sem categoria");
    }

    @Test
    void shouldRejectIncompleteProjectionBeforeSaving() {
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(entity()));
        final PayCycleSummary.Pending pending = new PayCycleSummary.Pending(
                PayCycleSummary.Pending.Source.INCOME_SCHEDULE,
                PayCycleSummary.Pending.Type.CALENDAR_NOT_REVIEWED, CODE, null, 2026, null, null);
        assertThatThrownBy(() -> service.recordClosure(summary(List.of(pending))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("pendências");
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectStaleCycleVersionBeforeSaving() {
        final PayCycleEntity cycle = entity();
        cycle.setVersion(5L);
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(cycle));
        assertThatThrownBy(() -> service.recordClosure(summary(List.of())))
                .isInstanceOf(ConflictException.class);
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectDuplicateSnapshotForCycleVersion() {
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(entity()));
        when(snapshotRepository.existsByPayCycleIdAndCycleVersion(1L, 4L)).thenReturn(true);
        assertThatThrownBy(() -> service.recordClosure(summary(List.of())))
                .isInstanceOf(ConflictException.class).hasMessageContaining("snapshot");
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    private static PayCycleEntity entity() {
        return PayCycleEntity.builder().id(1L).code(CODE).startDate(START).endDate(END)
                .kind(TRANSITION).lifecycle(CLOSED).version(4L).build();
    }

    private static PayCycleSummary summary(List<PayCycleSummary.Pending> pending) {
        final PayCycle cycle = new PayCycle(CODE, START, END, TRANSITION, null, null,
                CLOSED, false, false, 4L);
        return new PayCycleSummary(new PayCycleView(cycle,
                com.bts.personalbudget.core.domain.enumerator.PayCycleStatus.CLOSED, List.of()),
                BigDecimal.ZERO, new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("20.00"),
                List.of(new PayCycleSummary.CategoryDetail(CATEGORY, "Casa", DEBIT,
                                new BigDecimal("10.00"), new BigDecimal("15.00")),
                        new PayCycleSummary.CategoryDetail(null, "Sem categoria", DEBIT,
                                new BigDecimal("5.00"), new BigDecimal("5.00"))), pending);
    }
}
