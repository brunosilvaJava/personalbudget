package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.CONFIG_CODE;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayCycleConfigServiceTest {

    private static final LocalDate ACTIVATION_DATE = LocalDate.of(2026, 11, 5);

    @InjectMocks
    private PayCycleConfigService service;

    @Mock
    private PayCycleConfigRepository configRepository;

    @Mock
    private IncomeScheduleRepository scheduleRepository;

    @Spy
    private PayCycleMapper mapper = PayCycleMapper.INSTANCE;

    private final IncomeScheduleEntity anchorEntity = IncomeScheduleEntity.builder().id(1L).code(SCHEDULE_CODE).build();

    // ---- reading

    @Test
    void shouldFindTheConfigurationInCalendarMonthMode() {
        when(configRepository.findAllOrdered()).thenReturn(List.of(calendarMonthEntity()));

        final PayCycleConfig config = service.find();

        assertEquals(buildCalendarMonthConfig(), config);
        assertFalse(config.isPayCycle());
    }

    @Test
    void shouldFindTheConfigurationInPayCycleModeWithItsAnchorAndActivationDate() {
        when(configRepository.findAllOrdered()).thenReturn(List.of(payCycleEntity()));

        final PayCycleConfig config = service.find();

        assertEquals(buildPayCycleConfig(ACTIVATION_DATE, 3L), config);
        assertEquals(SCHEDULE_CODE, config.anchorScheduleCode());
        assertTrue(config.isPayCycle());
    }

    @Test
    void shouldFindTheFirstRowWhenThereIsMoreThanOne() {
        final PayCycleConfigEntity first = calendarMonthEntity();
        final PayCycleConfigEntity second = payCycleEntity();
        when(configRepository.findAllOrdered()).thenReturn(List.of(first, second));

        assertEquals(PayCycleMode.CALENDAR_MONTH, service.find().mode());
    }

    @Test
    void shouldFailToFindTheConfigurationWhenTheRowIsMissing() {
        when(configRepository.findAllOrdered()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.find());
    }

    @Test
    void shouldTellThatThePayCyclesAreOff() {
        when(configRepository.findAllOrdered()).thenReturn(List.of(calendarMonthEntity()));

        assertFalse(service.isPayCycleActive());
    }

    @Test
    void shouldTellThatThePayCyclesAreOn() {
        when(configRepository.findAllOrdered()).thenReturn(List.of(payCycleEntity()));

        assertTrue(service.isPayCycleActive());
    }

    // ---- locking

    @Test
    void shouldLockTheConfiguration() {
        when(configRepository.findAllForUpdate()).thenReturn(List.of(payCycleEntity()));

        final PayCycleConfig config = service.lock();

        assertEquals(buildPayCycleConfig(ACTIVATION_DATE, 3L), config);
        verify(configRepository, never()).findAllOrdered();
    }

    @Test
    void shouldFailToLockTheConfigurationWhenTheRowIsMissing() {
        when(configRepository.findAllForUpdate()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.lock());
    }

    // ---- version

    @Test
    void shouldAcceptTheCurrentVersion() {
        service.requireVersion(buildPayCycleConfig(ACTIVATION_DATE, 3L), 3L);
    }

    @Test
    void shouldRejectAnOlderVersion() {
        assertThrows(ConflictException.class,
                () -> service.requireVersion(buildPayCycleConfig(ACTIVATION_DATE, 3L), 2L));
    }

    @Test
    void shouldRejectANewerVersion() {
        assertThrows(ConflictException.class,
                () -> service.requireVersion(buildPayCycleConfig(ACTIVATION_DATE, 3L), 4L));
    }

    @Test
    void shouldRejectAMissingVersion() {
        assertThrows(ConflictException.class,
                () -> service.requireVersion(buildPayCycleConfig(ACTIVATION_DATE, 3L), null));
    }

    @Test
    void shouldCompareTheVersionByValueAndNotByReference() {
        // Long values above 127 are not cached: == between two boxed values would fail.
        service.requireVersion(buildPayCycleConfig(ACTIVATION_DATE, 100_000L), Long.valueOf(100_000L));
    }

    // ---- switching

    @Test
    void shouldSwitchToPayCycleWithTheAnchorAndTheActivationDate() {
        when(configRepository.findAllForUpdate()).thenReturn(List.of(calendarMonthEntity()));
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.of(anchorEntity));
        when(configRepository.saveAndFlush(any(PayCycleConfigEntity.class))).thenAnswer(call -> call.getArgument(0));

        final PayCycleConfig config = service.switchToPayCycle(SCHEDULE_CODE, ACTIVATION_DATE);

        final ArgumentCaptor<PayCycleConfigEntity> saved = ArgumentCaptor.forClass(PayCycleConfigEntity.class);
        verify(configRepository).saveAndFlush(saved.capture());
        assertEquals(PayCycleMode.PAY_CYCLE, saved.getValue().getMode());
        assertEquals(anchorEntity, saved.getValue().getAnchorSchedule());
        assertEquals(ACTIVATION_DATE, saved.getValue().getActivationDate());
        assertEquals(PayCycleMode.PAY_CYCLE, config.mode());
        assertEquals(SCHEDULE_CODE, config.anchorScheduleCode());
        assertEquals(ACTIVATION_DATE, config.activationDate());
    }

    @Test
    void shouldNotSwitchToPayCycleWhenTheAnchorScheduleDoesNotExist() {
        when(configRepository.findAllForUpdate()).thenReturn(List.of(calendarMonthEntity()));
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.switchToPayCycle(SCHEDULE_CODE, ACTIVATION_DATE));
        verify(configRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldSwitchBackToCalendarMonthForgettingTheAnchorAndTheActivationDate() {
        final PayCycleConfigEntity entity = payCycleEntity();
        when(configRepository.findAllForUpdate()).thenReturn(List.of(entity));
        when(configRepository.saveAndFlush(any(PayCycleConfigEntity.class))).thenAnswer(call -> call.getArgument(0));

        final PayCycleConfig config = service.switchToCalendarMonth();

        assertEquals(PayCycleMode.CALENDAR_MONTH, entity.getMode());
        assertNull(entity.getAnchorSchedule());
        assertNull(entity.getActivationDate());
        assertEquals(PayCycleMode.CALENDAR_MONTH, config.mode());
        assertNull(config.anchorScheduleCode());
        assertNull(config.activationDate());
    }

    @Test
    void shouldFailToSwitchWhenTheConfigurationRowIsMissing() {
        when(configRepository.findAllForUpdate()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.switchToCalendarMonth());
        verify(configRepository, never()).saveAndFlush(any());
    }

    // ---- helpers

    private PayCycleConfigEntity calendarMonthEntity() {
        return PayCycleConfigEntity.builder()
                .id(1L)
                .code(CONFIG_CODE)
                .mode(PayCycleMode.CALENDAR_MONTH)
                .version(0L)
                .build();
    }

    private PayCycleConfigEntity payCycleEntity() {
        return PayCycleConfigEntity.builder()
                .id(1L)
                .code(CONFIG_CODE)
                .mode(PayCycleMode.PAY_CYCLE)
                .anchorSchedule(anchorEntity)
                .activationDate(ACTIVATION_DATE)
                .version(3L)
                .build();
    }
}
