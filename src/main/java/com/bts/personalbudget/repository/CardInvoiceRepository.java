package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
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
public interface CardInvoiceRepository extends JpaRepository<CardInvoiceEntity, Long> {

    @Query("""
            SELECT i FROM CardInvoiceEntity i
            JOIN FETCH i.card
            WHERE i.code = :code
            """)
    Optional<CardInvoiceEntity> findByCode(@Param("code") UUID code);

    @Query("""
            SELECT i FROM CardInvoiceEntity i
            WHERE i.card.id = :cardId AND i.referenceMonth = :referenceMonth
            """)
    Optional<CardInvoiceEntity> findByCardIdAndReferenceMonth(@Param("cardId") Long cardId,
                                                              @Param("referenceMonth") LocalDate referenceMonth);

    /** Newest first; a null month returns every invoice of the card. */
    @Query("""
            SELECT i FROM CardInvoiceEntity i
            JOIN FETCH i.card
            WHERE i.card.id = :cardId
              AND (:referenceMonth IS NULL OR i.referenceMonth = :referenceMonth)
            ORDER BY i.referenceMonth DESC
            """)
    List<CardInvoiceEntity> findByCardId(@Param("cardId") Long cardId,
                                         @Param("referenceMonth") LocalDate referenceMonth);

    @Query("""
            SELECT i FROM CardInvoiceEntity i
            JOIN FETCH i.card
            WHERE i.id IN :ids
            """)
    List<CardInvoiceEntity> findAllWithCardByIdIn(@Param("ids") Collection<Long> ids);
}
