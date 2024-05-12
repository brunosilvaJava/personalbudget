package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialMovementRepository extends JpaRepository<FinancialMovementModel, Long> {

    FinancialMovementModel findByCode(UUID code);

}
