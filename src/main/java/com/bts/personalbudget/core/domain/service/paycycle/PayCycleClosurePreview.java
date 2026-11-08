package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.model.FinancialMovement;
import java.util.List;

public record PayCycleClosurePreview(
        PayCycleSummary summary,
        boolean canClose,
        List<String> blockers,
        List<FinancialMovement> lateMovements
) {
    public PayCycleClosurePreview {
        blockers = List.copyOf(blockers);
        lateMovements = List.copyOf(lateMovements);
    }
}
