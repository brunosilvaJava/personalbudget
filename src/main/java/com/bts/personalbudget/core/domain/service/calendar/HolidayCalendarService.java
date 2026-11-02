package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.core.domain.entity.HolidayCalendarReviewEntity;
import com.bts.personalbudget.core.domain.entity.HolidayEntity;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.HolidayCalendarMapper;
import com.bts.personalbudget.repository.HolidayCalendarReviewRepository;
import com.bts.personalbudget.repository.HolidayRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence of the local holiday calendar. The pure rules (what a holiday is, which years count
 * as reviewed) live in {@link HolidayCalendar}; this service loads and saves them.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class HolidayCalendarService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2200;
    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final HolidayRepository holidayRepository;
    private final HolidayCalendarReviewRepository reviewRepository;
    private final HolidayCalendarMapper holidayCalendarMapper;

    /**
     * The whole calendar. Small by nature (about a dozen rows per year), so callers that need
     * several years - the projection, the job - read it in one go.
     */
    public HolidayCalendar findCalendar() {
        log.info("m=findCalendar");
        final Set<Holiday> holidays = new HashSet<>(
                holidayCalendarMapper.toModelList(holidayRepository.findAllByOrderByHolidayDateAsc()));
        final Set<Integer> reviewedYears = reviewRepository.findAllByOrderByCalendarYearAsc().stream()
                .map(HolidayCalendarReviewEntity::getCalendarYear)
                .collect(Collectors.toSet());
        return new HolidayCalendar(holidays, reviewedYears);
    }

    /**
     * A year that was never reviewed answers reviewed = false with no holidays: "not prepared
     * yet", which is not the same as "reviewed, no holidays".
     */
    public HolidayYear findYear(final int year) {
        log.info("m=findYear, year={}", year);
        validateYear(year);
        final List<Holiday> holidays = holidayCalendarMapper.toModelList(
                holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
        return new HolidayYear(year, reviewRepository.existsByCalendarYear(year), holidays);
    }

    /**
     * Only the years that were reviewed, oldest first.
     */
    public List<HolidayYear> findReviewedYears() {
        log.info("m=findReviewedYears");
        final Map<Integer, List<Holiday>> holidaysByYear = holidayCalendarMapper
                .toModelList(holidayRepository.findAllByOrderByHolidayDateAsc()).stream()
                .collect(Collectors.groupingBy(holiday -> holiday.date().getYear()));
        return reviewRepository.findAllByOrderByCalendarYearAsc().stream()
                .map(review -> new HolidayYear(review.getCalendarYear(), true,
                        holidaysByYear.getOrDefault(review.getCalendarYear(), List.of())))
                .toList();
    }

    /**
     * Replaces the holidays of the year with exactly these and marks the year as reviewed, even
     * when the list is empty. Holidays that stay keep their row (and code); only the ones that
     * disappeared are deleted and the new ones inserted. Occurrences already materialized are not
     * touched: they keep the date they were generated on.
     *
     * @throws InvalidFieldsException when the year is out of range, a holiday is outside it or two
     *                                holidays share a date
     */
    @Transactional
    public HolidayYear reviewYear(final int year, final List<Holiday> holidays) {
        log.info("m=reviewYear, year={}, holidays={}", year, holidays.size());
        validateYear(year);
        // Validates the batch (same year, one holiday per date) with the rules of the calendar.
        final HolidayCalendar reviewed = HolidayCalendar.empty().replacingYear(year, holidays);

        final Map<LocalDate, HolidayEntity> existing = holidayRepository
                .findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .collect(Collectors.toMap(HolidayEntity::getHolidayDate, Function.identity()));
        final Map<LocalDate, Holiday> wanted = new HashMap<>();
        reviewed.holidays().forEach(holiday -> wanted.put(holiday.date(), holiday));

        existing.values().stream()
                .filter(entity -> !wanted.containsKey(entity.getHolidayDate()))
                .forEach(holidayRepository::delete);
        wanted.forEach((date, holiday) -> {
            final HolidayEntity entity = existing.get(date);
            if (entity == null) {
                holidayRepository.save(HolidayEntity.builder()
                        .holidayDate(date)
                        .name(holiday.name().trim())
                        .build());
            } else if (!entity.getName().equals(holiday.name().trim())) {
                entity.setName(holiday.name().trim());
            }
        });

        if (!reviewRepository.existsByCalendarYear(year)) {
            reviewRepository.save(HolidayCalendarReviewEntity.builder().calendarYear(year).build());
        }
        return findYear(year);
    }

    /**
     * @throws InvalidFieldsException (field year) when the year is outside the supported range
     */
    public void validateYear(final int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidFieldsException(INVALID_FIELDS_MESSAGE, HolidayCalendarService.class,
                    Map.of("year", "deve estar entre " + MIN_YEAR + " e " + MAX_YEAR));
        }
    }

}
