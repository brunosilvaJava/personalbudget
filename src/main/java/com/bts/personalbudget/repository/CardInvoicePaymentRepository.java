package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
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
public interface CardInvoicePaymentRepository extends JpaRepository<CardInvoicePaymentEntity, Long> {

    @Query("""
            SELECT COUNT(p) > 0 FROM CardInvoicePaymentEntity p
            WHERE p.invoice.id = :invoiceId AND p.reversedAt IS NULL
            """)
    boolean existsActiveByInvoiceId(@Param("invoiceId") Long invoiceId);

    /** Newest first; reversed payments stay in the history. */
    @Query("""
            SELECT p FROM CardInvoicePaymentEntity p
            JOIN FETCH p.invoice
            JOIN FETCH p.account
            WHERE p.invoice.id = :invoiceId
            ORDER BY p.id DESC
            """)
    List<CardInvoicePaymentEntity> findAllByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM CardInvoicePaymentEntity p
            WHERE p.code = :code AND p.invoice.id = :invoiceId
            """)
    Optional<CardInvoicePaymentEntity> findForUpdateByCodeAndInvoiceId(@Param("code") UUID code,
                                                                       @Param("invoiceId") Long invoiceId);
}
