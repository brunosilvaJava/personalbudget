package com.bts.personalbudget.controller.creditcard;

import com.bts.personalbudget.controller.creditcard.config.CreditCardControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.mapper.CreditCardMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Credit card", description = "API for managing credit cards")
@RequiredArgsConstructor
@RequestMapping("/credit_card")
@RestController
public class CreditCardController implements CreditCardControllerApiDocs {

    private final CreditCardService service;
    private final CreditCardMapper mapper;

    @Override
    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@Valid @RequestBody final CreditCardRequest request) {
        log.info("m=create, request={}", request);
        final CreditCard card = service.create(mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(card));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CreditCardResponse>> find(
            @RequestParam(value = "status", required = false) final List<CreditCardStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        return ResponseEntity.ok(mapper.toResponseList(service.find(statuses)));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<CreditCardResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.findByCode(code)));
    }

    @Override
    @PatchMapping("/{code}")
    public ResponseEntity<CreditCardResponse> update(@PathVariable final UUID code,
                                                     @RequestBody @Valid final CreditCardUpdateRequest request) {
        log.info("m=update, code={}, request={}", code, request);
        final CreditCard card = service.update(code, request.name(), request.closingDay(), request.dueDay(),
                request.creditLimit(), request.defaultAccountCode());
        return ResponseEntity.ok(mapper.toResponse(card));
    }

    @Override
    @PatchMapping("/{code}/inactivate")
    public ResponseEntity<CreditCardResponse> inactivate(@PathVariable final UUID code) {
        log.info("m=inactivate, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.inactivate(code)));
    }

    @Override
    @PatchMapping("/{code}/activate")
    public ResponseEntity<CreditCardResponse> activate(@PathVariable final UUID code) {
        log.info("m=activate, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.activate(code)));
    }
}
