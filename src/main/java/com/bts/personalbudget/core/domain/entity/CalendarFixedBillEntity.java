package com.bts.personalbudget.core.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "calendar_fixed_bill")
public class CalendarFixedBillEntity extends AuditingEntity{

    @Id
    @Column
    private Long id;

    @Column(name = "day_launch", columnDefinition = "SMALLINT")
    private Integer dayLaunch;

    @Column(name = "flg_active")
    private Boolean flgActive;

    @Column(name = "id_fixed_bill")
    private Long idFixedBill;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarFixedBillEntity that = (CalendarFixedBillEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
