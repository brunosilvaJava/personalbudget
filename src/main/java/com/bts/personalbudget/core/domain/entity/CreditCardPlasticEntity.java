package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.UUID;
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
@Table(name = "credit_card_plastic")
public class CreditCardPlasticEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_card_id", nullable = false, updatable = false)
    private CreditCardEntity card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private CreditCardHolderEntity holder;

    @Column(name = "last_digits", nullable = false, length = 4)
    private String lastDigits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private CreditCardPlasticKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private CreditCardPlasticStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_third_party_id")
    private ThirdPartyEntity defaultThirdParty;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (code == null) code = UUID.randomUUID();
        if (kind == null) kind = CreditCardPlasticKind.UNKNOWN;
        if (status == null) status = CreditCardPlasticStatus.ACTIVE;
        if (version == null) version = 0L;
    }
}
