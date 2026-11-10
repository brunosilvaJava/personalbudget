package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditCardHolderRepository extends JpaRepository<CreditCardHolderEntity, Long> {
    @Query("SELECT h FROM CreditCardHolderEntity h JOIN FETCH h.card WHERE h.code = :code")
    Optional<CreditCardHolderEntity> findByCode(@Param("code") UUID code);

    @Query("""
            SELECT h FROM CreditCardHolderEntity h WHERE h.card.id = :cardId
            ORDER BY CASE WHEN h.kind = com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind.TITULAR
                          THEN 0 ELSE 1 END, h.name ASC
            """)
    List<CreditCardHolderEntity> findByCardId(@Param("cardId") Long cardId);

    @Query("""
            SELECT h FROM CreditCardHolderEntity h WHERE h.card.id = :cardId
              AND h.kind = com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind.TITULAR
            """)
    Optional<CreditCardHolderEntity> findTitularByCardId(@Param("cardId") Long cardId);

    boolean existsByCardIdAndName(Long cardId, String name);
    boolean existsByCardIdAndNameAndCodeNot(Long cardId, String name, UUID code);
}
