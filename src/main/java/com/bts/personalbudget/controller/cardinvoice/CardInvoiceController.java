package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.controller.cardinvoice.config.CardInvoiceControllerApiDocs;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoiceService;
import com.bts.personalbudget.mapper.CardInvoiceMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Card invoice", description = "API for querying credit card invoices")
@RequiredArgsConstructor
@RestController
public class CardInvoiceController implements CardInvoiceControllerApiDocs {

    private final CardInvoiceService service;
    private final CardInvoiceMapper mapper;

    @Override
    @GetMapping("/credit_card/{code}/invoice")
    public ResponseEntity<List<CardInvoiceSummaryResponse>> find(
            @PathVariable final UUID code,
            @RequestParam(value = "reference_month", required = false)
            @DateTimeFormat(pattern = "yyyy-MM") final YearMonth referenceMonth) {
        log.info("m=find, code={}, referenceMonth={}", code, referenceMonth);
        return ResponseEntity.ok(mapper.toSummaryResponseList(service.find(code, referenceMonth)));
    }

    @Override
    @GetMapping("/card_invoice/{code}")
    public ResponseEntity<CardInvoiceResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.findByCode(code)));
    }
}
