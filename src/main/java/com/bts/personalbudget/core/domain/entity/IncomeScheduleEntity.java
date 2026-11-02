package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * startMonth and endMonth hold the first day of the month: the domain model works with YearMonth
 * and IncomeScheduleMapper converts, so no JPA attribute converter is involved.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "income_schedule")
public class IncomeScheduleEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @Column(nullable = false, length = 50)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "category_code")
    private UUID categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, columnDefinition = "varchar")
    private DateRuleType ruleType;

    @Column(name = "rule_value", nullable = false)
    private Integer ruleValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_week", nullable = false, columnDefinition = "varchar")
    private BusinessWeek businessWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "non_business_day_policy", columnDefinition = "varchar")
    private NonBusinessDayPolicy nonBusinessDayPolicy;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;

    @Column(name = "is_anchor", nullable = false)
    private Boolean anchor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private IncomeScheduleStatus status;

    @Column(name = "replaces_fixed_bill_code")
    private UUID replacesFixedBillCode;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (status == null) {
            status = IncomeScheduleStatus.ACTIVE;
        }
        if (anchor == null) {
            anchor = Boolean.FALSE;
        }
    }

}
