package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
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
 * The single row that holds the financial cycle configuration (created by the migration in
 * CALENDAR_MONTH). It is also the lock every write to the cycles takes first.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pay_cycle_config")
public class PayCycleConfigEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private PayCycleMode mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_schedule_id")
    private IncomeScheduleEntity anchorSchedule;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (mode == null) {
            mode = PayCycleMode.CALENDAR_MONTH;
        }
    }

}
