package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.HolidayCalendarReviewEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayCalendarReviewRepository extends JpaRepository<HolidayCalendarReviewEntity, Long> {

    boolean existsByCalendarYear(Integer calendarYear);

    List<HolidayCalendarReviewEntity> findAllByOrderByCalendarYearAsc();

}
