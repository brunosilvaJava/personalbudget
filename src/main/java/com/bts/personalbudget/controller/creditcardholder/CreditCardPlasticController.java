package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.service.creditcardholder.CreditCardHolderService;
import com.bts.personalbudget.mapper.CreditCardHolderMapper;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/credit_card/{cardCode}/plastic")
public class CreditCardPlasticController {
    private final CreditCardHolderService service;
    private final CreditCardHolderMapper mapper;

    @GetMapping
    public ResponseEntity<List<CreditCardPlasticResponse>> find(@PathVariable final UUID cardCode) {
        return ResponseEntity.ok(mapper.toPlasticResponseList(service.findPlastics(cardCode)));
    }

    @PostMapping
    public ResponseEntity<CreditCardPlasticResponse> create(@PathVariable final UUID cardCode,
                                                             @Valid @RequestBody final CreditCardPlasticRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(service.createPlastic(
                cardCode, request.holderCode(), request.lastDigits(), request.kind())));
    }

    @PatchMapping("/{plasticCode}")
    public ResponseEntity<CreditCardPlasticResponse> update(@PathVariable final UUID cardCode,
                                                             @PathVariable final UUID plasticCode,
                                                             @RequestBody final CreditCardPlasticUpdateRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.updatePlastic(cardCode, plasticCode, request.kind(),
                request.status(), request.defaultThirdPartyCode(), Boolean.TRUE.equals(request.clearDefaultThirdParty()))));
    }
}
