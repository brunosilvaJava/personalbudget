package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fixed_bill")
public class FixedBillEntity extends AuditingEntity implements Serializable {

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

    @Column(nullable = false, length = 50)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private FixedBillStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, columnDefinition = "varchar")
    private RecurrenceType recurrenceType;

    @Column(name = "reference_year")
    private Integer referenceYear;

    @OneToMany(mappedBy = "fixedBill", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<CalendarFixedBillEntity> calendarFixedBillEntityList;

    public void delete() {
        if (flagActive) {
            flagActive = false;
        }
    }

    public Optional<CalendarFixedBillEntity> findCalendarByDay(final Integer day) {
        if (calendarFixedBillEntityList == null || calendarFixedBillEntityList.isEmpty()) {
            return Optional.empty();
        }
        return calendarFixedBillEntityList.stream()
                .filter(calendarFixedBillEntity -> calendarFixedBillEntity.getDayLaunch().equals(day))
                .findFirst();
    }

}