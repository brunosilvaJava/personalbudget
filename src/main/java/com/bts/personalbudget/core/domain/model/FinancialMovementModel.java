package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "financial_movement")
public class FinancialMovementModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "amount_paid")
    private BigDecimal amountPaid;

    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "pay_date")
    private LocalDateTime payDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialMovementStatus status;

    @Column(name = "flag_active", nullable = false)
    private Boolean flagActive = Boolean.TRUE;

}
