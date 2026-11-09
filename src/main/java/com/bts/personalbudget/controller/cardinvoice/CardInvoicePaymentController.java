package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.controller.cardinvoice.config.CardInvoicePaymentControllerApiDocs;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoicePaymentService;
import com.bts.personalbudget.mapper.CardInvoicePaymentMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Card invoice payment", description = "API for paying and reversing credit card invoices")
@RequiredArgsConstructor
@RequestMapping("/card_invoice/{code}/payment")
@RestController
public class CardInvoicePaymentController implements CardInvoicePaymentControllerApiDocs {

    private final CardInvoicePaymentService service;
    private final CardInvoicePaymentMapper mapper;

    @Override
    @PostMapping
    public ResponseEntity<CardInvoicePaymentResponse> pay(
            @PathVariable final UUID code, @RequestBody @Valid final CardInvoicePaymentRequest request) {
        log.info("m=pay, code={}, request={}", code, request);
        final CardInvoicePaymentResponse response = mapper.toResponse(
                service.pay(code, request.accountCode(), request.paymentDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CardInvoicePaymentResponse>> list(@PathVariable final UUID code) {
        log.info("m=list, code={}", code);
        return ResponseEntity.ok(mapper.toResponseList(service.findAll(code)));
    }

    @Override
    @PostMapping("/{paymentCode}/reversal")
    public ResponseEntity<CardInvoicePaymentResponse> reverse(
            @PathVariable final UUID code, @PathVariable final UUID paymentCode) {
        log.info("m=reverse, code={}, paymentCode={}", code, paymentCode);
        return ResponseEntity.ok(mapper.toResponse(service.reverse(code, paymentCode)));
    }
}
