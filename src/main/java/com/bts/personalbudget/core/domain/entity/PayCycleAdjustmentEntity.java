package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One row of the pay cycle history. Insert only: the entity is immutable (Hibernate never issues an
 * UPDATE for it), has no setters and does not extend {@link AuditingEntity}, because the table has
 * no last-modified column.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Immutable
@Entity
@Table(name = "pay_cycle_adjustment")
@EntityListeners(AuditingEntityListener.class)
public class PayCycleAdjustmentEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar")
    private PayCycleAdjustmentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_cycle_id", updatable = false)
    private PayCycleEntity previousCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_cycle_id", updatable = false)
    private PayCycleEntity nextCycle;

    @Column(name = "old_boundary", updatable = false)
    private LocalDate oldBoundary;

    @Column(name = "new_boundary", updatable = false)
    private LocalDate newBoundary;

    @Column(updatable = false)
    private String justification;

    @Column(name = "expected_previous_version", updatable = false)
    private Long expectedPreviousVersion;

    @Column(name = "expected_next_version", updatable = false)
    private Long expectedNextVersion;

    @Column(name = "expected_config_version", updatable = false)
    private Long expectedConfigVersion;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
    }

}
