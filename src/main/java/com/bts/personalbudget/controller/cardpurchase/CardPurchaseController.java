package com.bts.personalbudget.controller.cardpurchase;

import com.bts.personalbudget.controller.cardpurchase.config.CardPurchaseControllerApiDocs;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseService;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Card purchase", description = "API for credit card purchases and their installments")
@RequiredArgsConstructor
@RequestMapping("/card_purchase")
@RestController
public class CardPurchaseController implements CardPurchaseControllerApiDocs {

    private final CardPurchaseService service;
    private final CardPurchaseMapper mapper;
    private final com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseBatchService batchService;
    private final com.bts.personalbudget.mapper.CardPurchaseBatchMapper batchMapper;

    @PostMapping("/batch")
    public ResponseEntity<List<CardPurchaseBatchLineResponse>> batch(
            @Valid @RequestBody final CardPurchaseBatchRequest request) {
        log.info("m=batch, creditCardCode={}, dryRun={}, items={}", request.creditCardCode(), request.dryRun(), request.items().size());
        return ResponseEntity.ok(batchMapper.toResponseList(batchService.process(request.creditCardCode(),
                Boolean.TRUE.equals(request.dryRun()), request.items().stream().map(batchMapper::toModel).toList())));
    }

    @Override
    @PostMapping
    public ResponseEntity<CardPurchaseResponse> create(@Valid @RequestBody final CardPurchaseRequest request) {
        log.info("m=create, request={}", request);
        final CardPurchase purchase = service.create(mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(purchase));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CardPurchaseResponse>> find(
            @RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate,
            @RequestParam(value = "card_code", required = false) final UUID cardCode) {
        log.info("m=find, startDate={}, endDate={}, cardCode={}", startDate, endDate, cardCode);
        return ResponseEntity.ok(mapper.toResponseList(service.find(startDate, endDate, cardCode)));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<CardPurchaseResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.findByCode(code)));
    }

    @Override
    @PatchMapping("/{code}")
    public ResponseEntity<CardPurchaseResponse> update(@PathVariable final UUID code,
                                                       @RequestBody @Valid final CardPurchaseUpdateRequest request) {
        log.info("m=update, code={}, request={}", code, request);
        final boolean hasNewFields = request.cardHolderCode() != null || request.cardLastDigits() != null
                || request.thirdPartyCode() != null || request.ownExpense() != null;
        final CardPurchase purchase = hasNewFields
                ? service.update(code, request.description(), request.purchaseDate(), request.installments(),
                        request.installmentAmount(), request.categoryCode(), request.cardHolderCode(),
                        request.cardLastDigits(), request.thirdPartyCode(), request.ownExpense())
                : service.update(code, request.description(), request.purchaseDate(), request.installments(),
                        request.installmentAmount(), request.categoryCode());
        return ResponseEntity.ok(mapper.toResponse(purchase));
    }

    @Override
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable final UUID code) {
        log.info("m=delete, code={}", code);
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
