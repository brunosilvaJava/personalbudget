package com.bts.personalbudget.core.domain.service.paycycle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarReviewedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayCalendarPayCycleListenerTest {

    @Mock
    private PayCycleService payCycleService;

    @InjectMocks
    private HolidayCalendarPayCycleListener listener;

    @Test
    void shouldRefreshCyclesWhenCalendarWasReviewed() {
        listener.onCalendarReviewed(new HolidayCalendarReviewedEvent(2026));

        verify(payCycleService).refresh();
    }

    @Test
    void shouldNotPropagateRefreshFailureAfterCalendarReview() {
        doThrow(new IllegalStateException("Refresh unavailable")).when(payCycleService).refresh();

        assertDoesNotThrow(() -> listener.onCalendarReviewed(new HolidayCalendarReviewedEvent(2026)));

        verify(payCycleService).refresh();
    }
}
