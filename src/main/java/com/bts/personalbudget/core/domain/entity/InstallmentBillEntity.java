package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import org.hibernate.annotations.GenericGenerator;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "installment_bill")
public class InstallmentBillEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @GenericGenerator(name = "uuid2")
    @Column(nullable = false)
    private UUID code;

    @Column(name = "flag_active", nullable = false)
    private Boolean flagActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, columnDefinition = "varchar")
    private OperationType operationType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private InstallmentBillStatus status;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "installment_total", nullable = false, columnDefinition = "SMALLINT")
    private Integer installmentTotal;

    @Column(name = "installment_count", nullable = false, columnDefinition = "SMALLINT")
    private Integer installmentCount;

    @Column(name = "last_installment_date", nullable = false)
    private LocalDate lastInstallmentDate;

    @Column(name = "next_installment_date", nullable = false)
    private LocalDate nextInstallmentDate;

}
