package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CardPurchaseRepository extends JpaRepository<CardPurchaseEntity, Long> {

    @Query("""
            SELECT p FROM CardPurchaseEntity p
            JOIN FETCH p.card
            WHERE p.code = :code AND p.flagActive = true
            """)
    Optional<CardPurchaseEntity> findActiveByCode(@Param("code") UUID code);

    @Query("""
            SELECT p FROM CardPurchaseEntity p
            JOIN FETCH p.card c
            WHERE p.flagActive = true
              AND p.purchaseDate BETWEEN :startDate AND :endDate
              AND (:cardCode IS NULL OR c.code = :cardCode)
            ORDER BY p.purchaseDate DESC, p.id DESC
            """)
    List<CardPurchaseEntity> findActiveByFilters(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("cardCode") UUID cardCode);

    @Query("""
            SELECT p FROM CardPurchaseEntity p
            JOIN FETCH p.card
            WHERE p.id IN :ids
            """)
    List<CardPurchaseEntity> findAllWithCardByIdIn(@Param("ids") Collection<Long> ids);
}
