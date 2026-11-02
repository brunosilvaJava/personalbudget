package com.bts.personalbudget.core.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * nominalMonth holds the first day of the month, as in IncomeScheduleEntity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "income_occurrence")
public class IncomeOccurrenceEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "income_schedule_id", nullable = false, updatable = false)
    private IncomeScheduleEntity incomeSchedule;

    @Column(name = "nominal_month", nullable = false, updatable = false)
    private LocalDate nominalMonth;

    @Column(name = "planned_date", nullable = false, updatable = false)
    private LocalDate plannedDate;

    @Column(name = "planned_amount", nullable = false, updatable = false)
    private BigDecimal plannedAmount;

    @Column(name = "movement_code", nullable = false, updatable = false)
    private UUID movementCode;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
    }

}
