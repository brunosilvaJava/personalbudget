package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CreditCardPlasticEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditCardPlasticRepository extends JpaRepository<CreditCardPlasticEntity, Long> {
    @Query("""
            SELECT p FROM CreditCardPlasticEntity p JOIN FETCH p.card JOIN FETCH p.holder
            LEFT JOIN FETCH p.defaultThirdParty WHERE p.code = :code
            """)
    Optional<CreditCardPlasticEntity> findByCode(@Param("code") UUID code);

    @Query("""
            SELECT p FROM CreditCardPlasticEntity p JOIN FETCH p.holder
            LEFT JOIN FETCH p.defaultThirdParty WHERE p.card.id = :cardId ORDER BY p.lastDigits ASC
            """)
    List<CreditCardPlasticEntity> findByCardId(@Param("cardId") Long cardId);

    @Query("""
            SELECT p FROM CreditCardPlasticEntity p JOIN FETCH p.holder
            LEFT JOIN FETCH p.defaultThirdParty
            WHERE p.card.id = :cardId AND p.lastDigits = :lastDigits
            """)
    Optional<CreditCardPlasticEntity> findByCardIdAndLastDigits(@Param("cardId") Long cardId,
                                                                 @Param("lastDigits") String lastDigits);
}
