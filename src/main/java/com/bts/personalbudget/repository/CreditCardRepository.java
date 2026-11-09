package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditCardRepository extends JpaRepository<CreditCardEntity, Long> {

    @Query("""
            SELECT c FROM CreditCardEntity c
            LEFT JOIN FETCH c.defaultAccount
            WHERE c.code = :code
            """)
    Optional<CreditCardEntity> findByCode(@Param("code") UUID code);

    /**
     * Locks the card row until the end of the transaction, so two purchases cannot both pass the
     * limit check. No fetch join: the lock is on the card only.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditCardEntity c WHERE c.code = :code")
    Optional<CreditCardEntity> findForUpdateByCode(@Param("code") UUID code);

    @Query("""
            SELECT c FROM CreditCardEntity c
            LEFT JOIN FETCH c.defaultAccount
            WHERE c.status IN :statuses
            ORDER BY c.name ASC
            """)
    List<CreditCardEntity> findByStatuses(@Param("statuses") List<CreditCardStatus> statuses);

    /** Compared by the database collation, the same one the unique key on name uses. */
    boolean existsByName(String name);

    boolean existsByNameAndCodeNot(String name, UUID code);
}
