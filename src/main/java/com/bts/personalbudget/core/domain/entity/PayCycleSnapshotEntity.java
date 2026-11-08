package com.bts.personalbudget.core.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Immutable
@Entity
@Table(name = "pay_cycle_snapshot")
@EntityListeners(AuditingEntityListener.class)
public class PayCycleSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private UUID code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pay_cycle_id", nullable = false, updatable = false)
    private PayCycleEntity payCycle;
    @Column(nullable = false, updatable = false)
    private Integer snapshotVersion;
    @Column(nullable = false, updatable = false)
    private Long cycleVersion;
    @Column(nullable = false, updatable = false)
    private BigDecimal recognizedRevenue;
    @Column(nullable = false, updatable = false)
    private BigDecimal recognizedExpense;
    @Column(nullable = false, updatable = false)
    private BigDecimal projectedRevenue;
    @Column(nullable = false, updatable = false)
    private BigDecimal projectedExpense;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdDate;
}
