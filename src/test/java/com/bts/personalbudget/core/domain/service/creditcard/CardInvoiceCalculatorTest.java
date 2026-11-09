package com.bts.personalbudget.core.domain.service.creditcard;

import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator.InvoiceSlot;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardInvoiceCalculatorTest {

    @Test
    void shouldPutAPurchaseBeforeTheClosingDateInTheInvoiceOfThatMonth() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 9), 10, 17);

        assertThat(slot.referenceMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void shouldPutAPurchaseOnTheClosingDateInTheNextInvoice() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 10), 10, 17);

        assertThat(slot.referenceMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 12, 17));
    }

    @Test
    void shouldPutAPurchaseAfterTheClosingDateInTheNextInvoice() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 11), 10, 17);

        assertThat(slot.referenceMonth()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void shouldDueInTheNextMonthWhenTheDueDayIsNotAfterTheClosingDay() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 24), 25, 5);

        assertThat(slot.referenceMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void shouldDueInTheNextMonthWhenTheDueDayEqualsTheClosingDay() {
        final InvoiceSlot slot = CardInvoiceCalculator.slotOf(YearMonth.of(2026, 11), 15, 15);

        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test
    void shouldCrossTheYearBoundary() {
        final InvoiceSlot december = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 12, 30), 10, 17);
        final InvoiceSlot dueInJanuary = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 25), 25, 5);

        assertThat(december.referenceMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(december.closingDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(december.dueDate()).isEqualTo(LocalDate.of(2027, 1, 17));
        assertThat(dueInJanuary.referenceMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(dueInJanuary.dueDate()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void shouldUseTheLastDayOfAShortMonthForTheClosingDay() {
        final InvoiceSlot beforeClosing = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 2, 27), 31, 10);
        final InvoiceSlot onClosing = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 2, 28), 31, 10);

        assertThat(beforeClosing.referenceMonth()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(beforeClosing.closingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(beforeClosing.dueDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(onClosing.referenceMonth()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(onClosing.closingDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(onClosing.dueDate()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void shouldUseTheLeapDayAsTheClosingDateInALeapYear() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2028, 2, 28), 31, 10);
        final InvoiceSlot onLeapDay = CardInvoiceCalculator.invoiceOf(LocalDate.of(2028, 2, 29), 31, 10);

        assertThat(slot.referenceMonth()).isEqualTo(YearMonth.of(2028, 2));
        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(onLeapDay.referenceMonth()).isEqualTo(YearMonth.of(2028, 3));
    }

    @Test
    void shouldUseTheLastDayOfAShortMonthForTheDueDay() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 5), 10, 31);

        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void shouldLetTheClampedDueDateCoincideWithTheClosingDateInAShortMonth() {
        final InvoiceSlot slot = CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 2, 1), 30, 31);

        assertThat(slot.closingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(slot.dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void shouldGiveASingleInvoiceForAOneTimePurchase() {
        final List<InvoiceSlot> slots = CardInvoiceCalculator.installmentSlots(LocalDate.of(2026, 11, 9), 1, 10, 17);

        assertThat(slots).containsExactly(CardInvoiceCalculator.invoiceOf(LocalDate.of(2026, 11, 9), 10, 17));
    }

    @Test
    void shouldSpreadTwelveInstallmentsAcrossTwoYearsOneInvoicePerMonth() {
        final List<InvoiceSlot> slots = CardInvoiceCalculator.installmentSlots(LocalDate.of(2026, 11, 25), 12, 25, 5);

        assertThat(slots).hasSize(12);
        assertThat(slots.get(0).referenceMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(slots.get(0).dueDate()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(slots.get(11).referenceMonth()).isEqualTo(YearMonth.of(2027, 11));
        assertThat(slots.get(11).dueDate()).isEqualTo(LocalDate.of(2027, 12, 5));
        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).referenceMonth()).isEqualTo(slots.get(i - 1).referenceMonth().plusMonths(1));
        }
    }

    @Test
    void shouldRecomputeTheClosingDateOfEachInstallmentInsteadOfCarryingAShortMonthForward() {
        final List<InvoiceSlot> slots = CardInvoiceCalculator.installmentSlots(LocalDate.of(2026, 1, 31), 3, 31, 10);

        assertThat(slots).extracting(InvoiceSlot::closingDate)
                .containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
        assertThat(slots).extracting(InvoiceSlot::dueDate)
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 10));
    }

    @Test
    void shouldRejectInvalidArguments() {
        final LocalDate date = LocalDate.of(2026, 11, 9);

        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.invoiceOf(date, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.invoiceOf(date, 32, 10));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.invoiceOf(date, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.invoiceOf(date, 10, 32));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.invoiceOf(null, 10, 17));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.slotOf(null, 10, 17));
        assertThrows(IllegalArgumentException.class, () -> CardInvoiceCalculator.installmentSlots(date, 0, 10, 17));
    }
}
