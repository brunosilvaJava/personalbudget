package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Immutable
@Entity
@Table(name = "pay_cycle_snapshot_category")
public class PayCycleSnapshotCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private PayCycleSnapshotEntity snapshot;
    @Column(nullable = false, updatable = false)
    private String categoryKey;
    @Column(updatable = false)
    private UUID categoryCode;
    @Column(nullable = false, updatable = false)
    private String categoryName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar")
    private OperationType operationType;
    @Column(nullable = false, updatable = false)
    private BigDecimal recognizedAmount;
    @Column(nullable = false, updatable = false)
    private BigDecimal projectedAmount;
}
