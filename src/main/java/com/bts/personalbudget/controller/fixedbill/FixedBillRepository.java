package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixedBillRepository extends JpaRepository<FixedBillEntity, Long> {

}
