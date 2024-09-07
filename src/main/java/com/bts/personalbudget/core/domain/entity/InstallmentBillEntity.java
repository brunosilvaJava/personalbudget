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
import java.time.LocalDateTime;
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
    protected Boolean flagActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "journal_entry_type", nullable = false)
    private OperationType journalEntryType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentBillStatus status;

    @Column(name = "bill_date", nullable = false)
    private LocalDateTime billDate;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "next_installment_date", nullable = false)
    private LocalDateTime nextInstallmentDate;

}
