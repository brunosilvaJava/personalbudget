package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.thirdparty.ReceivableResponse;
import com.bts.personalbudget.core.domain.service.thirdparty.Receivable;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReceivableMapper {
    public ReceivableResponse toResponse(final Receivable model) {
        return new ReceivableResponse(model.code(), model.thirdPartyCode(), model.status(), model.amount(),
                model.receivedAmount(), model.writtenOffAmount(), model.remainingAmount(), model.dueDate(),
                model.purchaseCode(), model.installmentNumber(), model.invoiceCode(), model.description());
    }
    public List<ReceivableResponse> toResponseList(final List<Receivable> models) {
        return models.stream().map(this::toResponse).toList();
    }
}
