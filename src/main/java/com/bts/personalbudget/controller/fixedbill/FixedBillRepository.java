package com.bts.personalbudget.controller.fixedbill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixedBillRepository extends JpaRepository<FixedBillEntity, Long> {

}
