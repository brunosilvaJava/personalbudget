package com.bts.personalbudget.core.domain.service.creditcard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure invoice rules of a credit card (no Spring, no persistence).
 *
 * <ul>
 *   <li>The invoice of month M closes on {@code closingDay} of month M; a shorter month uses its
 *       last day.</li>
 *   <li>A purchase before the closing date falls in the invoice that closes that month; on the
 *       closing date or after, in the invoice of the next month.</li>
 *   <li>The invoice is due on {@code dueDay}: in the same month as the closing when
 *       {@code dueDay > closingDay}, otherwise in the next month; a shorter month uses its last
 *       day. There is no business-day adjustment. In a short month the clamped due date can
 *       coincide with the closing date (closing 30 and due 31 in February: both on the 28th).</li>
 *   <li>Installment k falls in the invoice k-1 months after the first one.</li>
 *   <li>The competence of an installment is the due date of its invoice.</li>
 * </ul>
 */
public final class CardInvoiceCalculator {

    /** One invoice: the month it refers to, when it closes and when it is due. */
    public record InvoiceSlot(YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate) {
    }

    private CardInvoiceCalculator() {
    }

    /** The invoice a purchase made on {@code purchaseDate} falls in. */
    public static InvoiceSlot invoiceOf(final LocalDate purchaseDate, final int closingDay, final int dueDay) {
        requireDay("closingDay", closingDay);
        requireDay("dueDay", dueDay);
        if (purchaseDate == null) {
            throw new IllegalArgumentException("purchaseDate must not be null");
        }
        final YearMonth purchaseMonth = YearMonth.from(purchaseDate);
        final YearMonth reference = purchaseDate.isBefore(clamp(purchaseMonth, closingDay))
                ? purchaseMonth : purchaseMonth.plusMonths(1);
        return slotOf(reference, closingDay, dueDay);
    }

    /** The invoice that closes in {@code referenceMonth}. */
    public static InvoiceSlot slotOf(final YearMonth referenceMonth, final int closingDay, final int dueDay) {
        requireDay("closingDay", closingDay);
        requireDay("dueDay", dueDay);
        if (referenceMonth == null) {
            throw new IllegalArgumentException("referenceMonth must not be null");
        }
        final LocalDate closingDate = clamp(referenceMonth, closingDay);
        final YearMonth dueMonth = dueDay > closingDay ? referenceMonth : referenceMonth.plusMonths(1);
        return new InvoiceSlot(referenceMonth, closingDate, clamp(dueMonth, dueDay));
    }

    /**
     * The invoices of a purchase in {@code installments} parts, in installment order: the first one
     * is the invoice of the purchase date and each next one is a month later.
     */
    public static List<InvoiceSlot> installmentSlots(final LocalDate purchaseDate, final int installments,
                                                     final int closingDay, final int dueDay) {
        if (installments < 1) {
            throw new IllegalArgumentException("installments must be at least 1");
        }
        final InvoiceSlot first = invoiceOf(purchaseDate, closingDay, dueDay);
        final List<InvoiceSlot> slots = new ArrayList<>(installments);
        slots.add(first);
        for (int k = 1; k < installments; k++) {
            slots.add(slotOf(first.referenceMonth().plusMonths(k), closingDay, dueDay));
        }
        return List.copyOf(slots);
    }

    private static LocalDate clamp(final YearMonth month, final int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private static void requireDay(final String name, final int day) {
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException(name + " must be between 1 and 31");
        }
    }
}
