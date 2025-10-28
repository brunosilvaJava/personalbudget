package com.bts.personalbudget.core.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

import lombok.*;


@Data
@EqualsAndHashCode(of = {"id", "fixedBill"}, callSuper = false)
@Entity
@Table(name = "calendar_fixed_bill")
public class CalendarFixedBillEntity extends AuditingEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "day_launch", columnDefinition = "SMALLINT")
    private Integer dayLaunch;

    @Column(name = "flg_active")
    private Boolean flgActive;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_fixed_bill")
    private FixedBillEntity fixedBill;

}
