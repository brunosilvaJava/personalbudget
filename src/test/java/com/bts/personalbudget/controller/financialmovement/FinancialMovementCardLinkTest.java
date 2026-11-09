package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMovementCardLinkTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final FinancialMovementMapper mapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldSerializeTheCardLinkInSnakeCase() {
        final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
        final UUID purchase = UUID.randomUUID();
        final UUID invoice = UUID.randomUUID();
        final FinancialMovementResponse response = new FinancialMovementResponse(
                UUID.randomUUID(), OperationType.DEBIT, "Geladeira (2/3)", new BigDecimal("100.00"), null,
                now, now, FinancialMovementStatus.PENDING, null, null, null, LocalDate.of(2026, 12, 5), true,
                purchase, invoice, 2);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("card_purchase_code").asText()).isEqualTo(purchase.toString());
        assertThat(json.get("card_invoice_code").asText()).isEqualTo(invoice.toString());
        assertThat(json.get("installment_number").asInt()).isEqualTo(2);
        assertThat(json.has("cardPurchaseCode")).isFalse();
    }

    @Test
    void shouldSerializeNullCardLinkForAnOrdinaryMovement() {
        final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
        final FinancialMovementResponse response = new FinancialMovementResponse(
                UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"), null,
                now, now, FinancialMovementStatus.PENDING, null, null, null, LocalDate.of(2026, 11, 1), false);

        final JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("card_purchase_code").isNull()).isTrue();
        assertThat(json.get("card_invoice_code").isNull()).isTrue();
        assertThat(json.get("installment_number").isNull()).isTrue();
    }

    @Test
    void shouldCarryTheInstallmentNumberFromTheEntityAndLeaveTheCodesToTheService() {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setCardPurchaseId(10L);
        entity.setCardInvoiceId(201L);
        entity.setInstallmentNumber(3);

        final FinancialMovement model = mapper.toModel(entity);

        assertThat(model.installmentNumber()).isEqualTo(3);
        assertThat(model.cardPurchaseCode()).isNull();
        final UUID purchase = UUID.randomUUID();
        final UUID invoice = UUID.randomUUID();
        final FinancialMovement linked = model.withCardLink(purchase, invoice);
        assertThat(linked.cardPurchaseCode()).isEqualTo(purchase);
        assertThat(linked.cardInvoiceCode()).isEqualTo(invoice);
        assertThat(linked.withStatus(FinancialMovementStatus.PENDING).cardPurchaseCode()).isEqualTo(purchase);
        assertThat(linked.withCompetence(LocalDate.of(2026, 12, 5), true).cardInvoiceCode()).isEqualTo(invoice);
    }

    @Test
    void shouldNeverWriteTheCardLinkBackFromAClientModel() {
        final FinancialMovement model = FinancialMovementFactory.buildModel()
                .withCardLink(UUID.randomUUID(), UUID.randomUUID());

        final FinancialMovementEntity entity = mapper.toEntity(model);

        assertThat(entity.getCardPurchaseId()).isNull();
        assertThat(entity.getCardInvoiceId()).isNull();
        assertThat(entity.getInstallmentNumber()).isNull();
    }
}
