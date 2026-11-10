package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.financialmovement.FinancialMovementRequest;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementResponse;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementUpdateRequest;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface FinancialMovementMapper {

    FinancialMovementMapper INSTANCE = Mappers.getMapper(FinancialMovementMapper.class);

    FinancialMovement toModel(FinancialMovementRequest request);
    FinancialMovementUpdate toUpdate(FinancialMovementUpdateRequest updateRequest, UUID code);
    @Mapping(target = "thirdPartyCode", source = "thirdParty.code")
    @Mapping(target = "receivableSourceCode", ignore = true)
    FinancialMovement toModel(FinancialMovementEntity entity);
    List<FinancialMovement> toModel(List<FinancialMovementEntity> financialMovementEntities);
    @Mapping(target = "amount", expression = "java(normalizeValue(financialMovement.amount(), financialMovement.operationType()))")
    @Mapping(target = "amountPaid", expression = "java(normalizeValue(financialMovement.amountPaid(), financialMovement.operationType()))")
    @Mapping(target = "installmentNumber", ignore = true)
    @Mapping(target = "thirdParty", ignore = true)
    @Mapping(target = "receivableSourceMovementId", ignore = true)
    @Mapping(target = "writtenOffAmount", ignore = true)
    @Mapping(target = "writtenOffDate", ignore = true)
    FinancialMovementEntity toEntity(FinancialMovement financialMovement);
    List<FinancialMovementResponse> toResponse(List<FinancialMovement> financialMovement);
    FinancialMovementResponse toResponse(FinancialMovement financialMovement);

    /**
     * Written by hand: the generated mapping left movementDate and dueDate null (LocalDate source,
     * LocalDateTime target), so a generated movement was saved without dates. The competence is left
     * to FinancialMovementService.create, which derives it from the occurrence date.
     */
    default FinancialMovement toFinancialMovement(InstallmentBill installmentBill, FinancialMovementStatus financialMovementStatus) {
        return recurrenceMovement(
                installmentBill.getOperationType(), installmentBill.getDescription(), installmentBill.getAmount(),
                installmentBill.getPurchaseDate(), installmentBill.getNextInstallmentDate(),
                financialMovementStatus, installmentBill.getCode(), installmentBill.getCategoryCode());
    }

    default List<FinancialMovement> toFinancialMovementList(List<InstallmentBill> installmentBillList, FinancialMovementStatus financialMovementStatus) {
        return installmentBillList.stream()
                .map(installmentBill -> toFinancialMovement(installmentBill, financialMovementStatus))
                .toList();
    }

    /** Written by hand for the same reason as the installment one above. */
    default FinancialMovement toFinancialMovement(FixedBill fixedBill, FinancialMovementStatus financialMovementStatus) {
        return recurrenceMovement(
                fixedBill.getOperationType(), fixedBill.getDescription(), fixedBill.getAmount(),
                fixedBill.getNextDueDate(), fixedBill.getNextDueDate(),
                financialMovementStatus, fixedBill.getCode(), fixedBill.getCategoryCode());
    }

    private FinancialMovement recurrenceMovement(final OperationType operationType, final String description,
                                                 final BigDecimal amount, final LocalDate movementDate,
                                                 final LocalDate occurrenceDate, final FinancialMovementStatus status,
                                                 final UUID recurrenceBillCode, final UUID categoryCode) {
        return new FinancialMovement(null, operationType, description, normalizeValue(amount, operationType), null,
                atStartOfDay(movementDate), atStartOfDay(occurrenceDate), null, status, Boolean.TRUE,
                recurrenceBillCode, occurrenceDate, categoryCode, null, Boolean.FALSE);
    }

    private LocalDateTime atStartOfDay(final LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    default List<FinancialMovement> toFinancialMovementListFromFixedBill(List<FixedBill> fixedBillList, FinancialMovementStatus financialMovementStatus) {
        return fixedBillList.stream()
                .map(fixedBill -> toFinancialMovement(fixedBill, financialMovementStatus))
                .toList();
    }

    default BigDecimal normalizeValue(final BigDecimal value, final OperationType operationType) {
        if (value == null) {
            return null;
        }
        if (operationType == DEBIT) {
            return value.compareTo(BigDecimal.ZERO) > 0 ? value.negate() : value;
        } else if (operationType == CREDIT) {
            return value.compareTo(BigDecimal.ZERO) < 0 ? value.negate() : value;
        }
        return value;
    }
}
