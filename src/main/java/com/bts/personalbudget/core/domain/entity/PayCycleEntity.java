package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A cycle as {@code [startDate, endDate)}. anchorNominalMonth holds the first day of the month: the
 * domain model works with YearMonth and PayCycleMapper converts. The database also keeps generated
 * columns (active_*) that back the UNIQUE keys; they are not mapped on purpose.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pay_cycle")
public class PayCycleEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private PayCycleKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_schedule_id")
    private IncomeScheduleEntity anchorSchedule;

    @Column(name = "anchor_nominal_month")
    private LocalDate anchorNominalMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private PayCycleLifecycle lifecycle;

    @Column(name = "start_adjusted", nullable = false)
    private Boolean startAdjusted;

    @Column(name = "end_adjusted", nullable = false)
    private Boolean endAdjusted;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (lifecycle == null) {
            lifecycle = PayCycleLifecycle.ACTIVE;
        }
        if (startAdjusted == null) {
            startAdjusted = Boolean.FALSE;
        }
        if (endAdjusted == null) {
            endAdjusted = Boolean.FALSE;
        }
    }

}
