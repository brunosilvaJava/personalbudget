package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.HolidayEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayRepository extends JpaRepository<HolidayEntity, Long> {

    List<HolidayEntity> findAllByOrderByHolidayDateAsc();

    List<HolidayEntity> findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate startDate, LocalDate endDate);

}
