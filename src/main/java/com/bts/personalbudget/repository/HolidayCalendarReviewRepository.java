package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.HolidayCalendarReviewEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayCalendarReviewRepository extends JpaRepository<HolidayCalendarReviewEntity, Long> {

    boolean existsByCalendarYear(Integer calendarYear);

    List<HolidayCalendarReviewEntity> findAllByOrderByCalendarYearAsc();

    @Modifying
    @Query(value = "INSERT IGNORE INTO holiday_calendar_review (calendar_year) VALUES (:calendarYear)",
            nativeQuery = true)
    void insertIfAbsent(@Param("calendarYear") Integer calendarYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM HolidayCalendarReviewEntity review WHERE review.calendarYear = :calendarYear")
    Optional<HolidayCalendarReviewEntity> findForUpdateByCalendarYear(@Param("calendarYear") Integer calendarYear);

}
