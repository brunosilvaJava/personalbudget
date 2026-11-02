package com.bts.personalbudget.core.domain.service.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.HolidayCalendarReviewEntity;
import com.bts.personalbudget.core.domain.entity.HolidayEntity;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.HolidayCalendarMapper;
import com.bts.personalbudget.repository.HolidayCalendarReviewRepository;
import com.bts.personalbudget.repository.HolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayCalendarServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);
    private static final LocalDate SEP_7 = LocalDate.of(2026, 9, 7);
    private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
    private static final LocalDate NOV_2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate DEC_25 = LocalDate.of(2026, 12, 25);

    @InjectMocks
    private HolidayCalendarService service;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private HolidayCalendarReviewRepository reviewRepository;

    @Spy
    private HolidayCalendarMapper holidayCalendarMapper = HolidayCalendarMapper.INSTANCE;

    // ---- reading

    @Test
    void shouldBuildTheCalendarFromTheHolidaysAndTheReviewedYears() {
        when(holidayRepository.findAllByOrderByHolidayDateAsc()).thenReturn(List.of(entity(SEP_7, "Independência")));
        when(reviewRepository.findAllByOrderByCalendarYearAsc()).thenReturn(List.of(review(2026), review(2027)));

        final HolidayCalendar calendar = service.findCalendar();

        assertThat(calendar.isHoliday(SEP_7)).isTrue();
        assertThat(calendar.isHoliday(OCT_12)).isFalse();
        assertThat(calendar.isReviewed(2026)).isTrue();
        assertThat(calendar.isReviewed(2027)).isTrue();
        assertThat(calendar.isReviewed(2028)).isFalse();
    }

    @Test
    void shouldAnswerAYearNeverReviewedAsNotPreparedWithNoHolidays() {
        when(holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31))).thenReturn(List.of());
        when(reviewRepository.existsByCalendarYear(2028)).thenReturn(false);

        final HolidayYear year = service.findYear(2028);

        assertThat(year.year()).isEqualTo(2028);
        assertThat(year.reviewed()).isFalse();
        assertThat(year.holidays()).isEmpty();
    }

    @Test
    void shouldAnswerAReviewedYearWithItsHolidays() {
        when(holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(JAN_1, DEC_31))
                .thenReturn(List.of(entity(SEP_7, "Independência"), entity(OCT_12, "Padroeira")));
        when(reviewRepository.existsByCalendarYear(2026)).thenReturn(true);

        final HolidayYear year = service.findYear(2026);

        assertThat(year.reviewed()).isTrue();
        assertThat(year.holidays()).containsExactly(
                new Holiday(SEP_7, "Independência"), new Holiday(OCT_12, "Padroeira"));
    }

    @Test
    void shouldListOnlyTheReviewedYearsWithTheirHolidays() {
        when(holidayRepository.findAllByOrderByHolidayDateAsc()).thenReturn(List.of(entity(SEP_7, "Independência")));
        when(reviewRepository.findAllByOrderByCalendarYearAsc()).thenReturn(List.of(review(2026), review(2027)));

        final List<HolidayYear> years = service.findReviewedYears();

        assertThat(years).containsExactly(
                new HolidayYear(2026, true, List.of(new Holiday(SEP_7, "Independência"))),
                new HolidayYear(2027, true, List.of()));
    }

    @Test
    void shouldRejectAYearOutsideTheSupportedRangeWithoutQuerying() {
        assertThatThrownBy(() -> service.findYear(1999)).isInstanceOfSatisfying(InvalidFieldsException.class,
                exception -> assertThat(exception.getValidations()).containsKey("year"));
        assertThatThrownBy(() -> service.findYear(2201)).isInstanceOf(InvalidFieldsException.class);
        assertThatThrownBy(() -> service.reviewYear(1999, List.of())).isInstanceOf(InvalidFieldsException.class);
        verifyNoInteractions(holidayRepository, reviewRepository);
    }

    @Test
    void shouldAcceptTheLimitsOfTheSupportedRange() {
        service.validateYear(2000);
        service.validateYear(2200);
    }

    // ---- reviewing

    @Test
    void shouldInsertTheHolidaysTrimAndMarkTheYearAsReviewed() {
        when(holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(JAN_1, DEC_31))
                .thenReturn(List.of(), List.of(entity(SEP_7, "Independência"), entity(NOV_2, "Finados")));
        when(reviewRepository.existsByCalendarYear(2026)).thenReturn(false, true);

        final HolidayYear year = service.reviewYear(2026,
                List.of(new Holiday(SEP_7, "  Independência "), new Holiday(NOV_2, "Finados")));

        final ArgumentCaptor<HolidayEntity> saved = ArgumentCaptor.forClass(HolidayEntity.class);
        verify(holidayRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(HolidayEntity::getHolidayDate)
                .containsExactlyInAnyOrder(SEP_7, NOV_2);
        assertThat(saved.getAllValues()).extracting(HolidayEntity::getName)
                .containsExactlyInAnyOrder("Independência", "Finados");
        final ArgumentCaptor<HolidayCalendarReviewEntity> review = ArgumentCaptor.forClass(HolidayCalendarReviewEntity.class);
        verify(reviewRepository).save(review.capture());
        assertThat(review.getValue().getCalendarYear()).isEqualTo(2026);
        assertThat(year.reviewed()).isTrue();
        assertThat(year.holidays()).hasSize(2);
    }

    @Test
    void shouldKeepTheRowsThatStayRenameTheChangedAndDeleteTheOnesThatLeft() {
        final HolidayEntity unchanged = entity(SEP_7, "Independência");
        final HolidayEntity renamed = entity(NOV_2, "Finados");
        final HolidayEntity removed = entity(DEC_25, "Natal");
        when(holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(JAN_1, DEC_31))
                .thenReturn(List.of(unchanged, renamed, removed), List.of(unchanged, renamed));
        when(reviewRepository.existsByCalendarYear(2026)).thenReturn(true);

        service.reviewYear(2026, List.of(
                new Holiday(SEP_7, "Independência"),
                new Holiday(NOV_2, "Finados do Brasil"),
                new Holiday(OCT_12, "Padroeira")));

        verify(holidayRepository).delete(removed);
        verify(holidayRepository, never()).delete(unchanged);
        verify(holidayRepository, never()).delete(renamed);
        assertThat(renamed.getName()).isEqualTo("Finados do Brasil");
        assertThat(unchanged.getName()).isEqualTo("Independência");
        final ArgumentCaptor<HolidayEntity> saved = ArgumentCaptor.forClass(HolidayEntity.class);
        verify(holidayRepository).save(saved.capture());
        assertThat(saved.getValue().getHolidayDate()).isEqualTo(OCT_12);
        // The year was already reviewed: no second review row.
        verify(reviewRepository, never()).save(any(HolidayCalendarReviewEntity.class));
    }

    @Test
    void shouldMarkAnEmptyListAsReviewedAndRemoveEveryHoliday() {
        final HolidayEntity existing = entity(SEP_7, "Independência");
        when(holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(JAN_1, DEC_31))
                .thenReturn(List.of(existing), List.of());
        when(reviewRepository.existsByCalendarYear(2026)).thenReturn(false, true);

        final HolidayYear year = service.reviewYear(2026, List.of());

        verify(holidayRepository).delete(existing);
        verify(reviewRepository).save(any(HolidayCalendarReviewEntity.class));
        assertThat(year.reviewed()).isTrue();
        assertThat(year.holidays()).isEmpty();
    }

    @Test
    void shouldRejectAHolidayOfAnotherYearAndChangeNothing() {
        assertThatThrownBy(() -> service.reviewYear(2026, List.of(new Holiday(LocalDate.of(2027, 1, 1), "Confraternização"))))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("holidays"));

        verifyNoInteractions(holidayRepository, reviewRepository);
    }

    @Test
    void shouldRejectTwoHolidaysOnTheSameDateAndChangeNothing() {
        assertThatThrownBy(() -> service.reviewYear(2026,
                List.of(new Holiday(SEP_7, "Independência"), new Holiday(SEP_7, "Sete de Setembro"))))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("holidays"));

        verifyNoInteractions(holidayRepository, reviewRepository);
    }

    private HolidayEntity entity(final LocalDate date, final String name) {
        return HolidayEntity.builder().id(date.toEpochDay()).holidayDate(date).name(name).build();
    }

    private HolidayCalendarReviewEntity review(final int year) {
        return HolidayCalendarReviewEntity.builder().calendarYear(year).build();
    }
}
