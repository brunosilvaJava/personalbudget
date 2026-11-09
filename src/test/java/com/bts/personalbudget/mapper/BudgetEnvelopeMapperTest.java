package com.bts.personalbudget.mapper;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.BudgetEnvelopeFactory;
import com.bts.personalbudget.core.domain.factory.CategoryFactory;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelope;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetEnvelopeMapperTest {

    private final BudgetEnvelopeMapper mapper = Mappers.getMapper(BudgetEnvelopeMapper.class);

    private static PayCycleSummary.CategoryDetail detail(final String recognized, final String projected) {
        return new PayCycleSummary.CategoryDetail(CategoryFactory.CATEGORY_CODE, CategoryFactory.CATEGORY_NAME,
                OperationType.DEBIT, new BigDecimal(recognized), new BigDecimal(projected));
    }

    @Test
    void shouldSplitConsumptionIntoRecognizedAndCommittedAndReserveWhatIsLeft() {
        final BudgetEnvelope envelope = mapper.assemble(BudgetEnvelopeFactory.buildEntity(),
                CategoryFactory.CATEGORY_NAME, detail("100.00", "300.00"));

        assertThat(envelope.plannedAmount()).isEqualByComparingTo("500.00");
        assertThat(envelope.recognizedAmount()).isEqualByComparingTo("100.00");
        assertThat(envelope.committedAmount()).isEqualByComparingTo("200.00");
        assertThat(envelope.consumedAmount()).isEqualByComparingTo("300.00");
        assertThat(envelope.remainingAmount()).isEqualByComparingTo("200.00");
        assertThat(envelope.reservedAmount()).isEqualByComparingTo("200.00");
        assertThat(envelope.overrunAmount()).isEqualByComparingTo("0");
        assertThat(envelope.cycleCode()).isEqualTo(BudgetEnvelopeFactory.CYCLE_CODE);
    }

    @Test
    void shouldReserveTheWholePlanWhenNothingWasConsumedYet() {
        final BudgetEnvelope envelope = mapper.assemble(BudgetEnvelopeFactory.buildEntity(), "Aluguel", null);

        assertThat(envelope.consumedAmount()).isEqualByComparingTo("0");
        assertThat(envelope.reservedAmount()).isEqualByComparingTo("500.00");
        assertThat(envelope.remainingAmount()).isEqualByComparingTo("500.00");
        assertThat(envelope.overrunAmount()).isEqualByComparingTo("0");
    }

    @Test
    void shouldReportTheOverrunAndReserveNothingWhenConsumptionPassesThePlan() {
        final BudgetEnvelope envelope = mapper.assemble(BudgetEnvelopeFactory.buildEntity(), "Aluguel",
                detail("500.00", "650.00"));

        assertThat(envelope.remainingAmount()).isEqualByComparingTo("-150.00");
        assertThat(envelope.reservedAmount()).isEqualByComparingTo("0");
        assertThat(envelope.overrunAmount()).isEqualByComparingTo("150.00");
        assertThat(envelope.committedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldExactlyUseUpTheEnvelopeWithoutReserveOrOverrun() {
        final BudgetEnvelope envelope = mapper.assemble(BudgetEnvelopeFactory.buildEntity(), "Aluguel",
                detail("0.00", "500.00"));

        assertThat(envelope.reservedAmount()).isEqualByComparingTo("0");
        assertThat(envelope.overrunAmount()).isEqualByComparingTo("0");
    }
}
