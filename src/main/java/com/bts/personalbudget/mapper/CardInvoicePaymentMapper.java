package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.cardinvoice.CardInvoicePaymentResponse;
import com.bts.personalbudget.core.domain.entity.CardInvoicePaymentEntity;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePayment;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CardInvoicePaymentMapper {

    @Mapping(target = "invoiceCode", source = "invoice.code")
    @Mapping(target = "accountCode", source = "account.code")
    CardInvoicePayment toModel(CardInvoicePaymentEntity entity);

    List<CardInvoicePayment> toModelList(List<CardInvoicePaymentEntity> entities);

    CardInvoicePaymentResponse toResponse(CardInvoicePayment model);

    List<CardInvoicePaymentResponse> toResponseList(List<CardInvoicePayment> models);
}
