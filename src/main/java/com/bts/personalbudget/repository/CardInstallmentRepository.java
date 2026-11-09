package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Card-specific queries over the installments, which are financial movements linked to a purchase
 * and an invoice. Only active installments count everywhere.
 */
@Repository
public interface CardInstallmentRepository extends JpaRepository<FinancialMovementEntity, Long> {

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.cardPurchaseId = :purchaseId AND fm.flagActive = true
            ORDER BY fm.installmentNumber ASC
            """)
    List<FinancialMovementEntity> findActiveByPurchaseId(@Param("purchaseId") Long purchaseId);

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.cardPurchaseId IN :purchaseIds AND fm.flagActive = true
            ORDER BY fm.cardPurchaseId ASC, fm.installmentNumber ASC
            """)
    List<FinancialMovementEntity> findActiveByPurchaseIdIn(@Param("purchaseIds") Collection<Long> purchaseIds);

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.cardInvoiceId = :invoiceId AND fm.flagActive = true
            ORDER BY fm.cardPurchaseId ASC, fm.installmentNumber ASC
            """)
    List<FinancialMovementEntity> findActiveByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.cardInvoiceId IN :invoiceIds AND fm.flagActive = true
            """)
    List<FinancialMovementEntity> findActiveByInvoiceIdIn(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            SELECT COUNT(fm) FROM FinancialMovementEntity fm
            WHERE fm.cardInvoiceId = :invoiceId AND fm.flagActive = true
            """)
    long countActiveByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            SELECT COUNT(fm) FROM FinancialMovementEntity fm
            WHERE fm.cardInvoiceId = :invoiceId AND fm.flagActive = true AND fm.status = :status
            """)
    long countActiveByInvoiceIdAndStatus(@Param("invoiceId") Long invoiceId,
                                         @Param("status") FinancialMovementStatus status);

    /**
     * What the card still owes: the unpaid part of every active installment of every active
     * purchase of the card, future ones included. It is the used limit.
     */
    @Query("""
            SELECT COALESCE(SUM(ABS(fm.amount) - ABS(COALESCE(fm.amountPaid, 0))), 0)
            FROM FinancialMovementEntity fm, CardPurchaseEntity p
            WHERE fm.cardPurchaseId = p.id
              AND p.card.id = :cardId
              AND p.flagActive = true
              AND fm.flagActive = true
            """)
    BigDecimal sumUnpaidByCardId(@Param("cardId") Long cardId);
}
