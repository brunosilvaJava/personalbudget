package com.bts.personalbudget.controller.creditcardholder;

import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
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
@RequestMapping("/credit_card/{cardCode}/holder")
public class CreditCardHolderController {
    private final CreditCardHolderService service;
    private final CreditCardHolderMapper mapper;

    @GetMapping
    public ResponseEntity<List<CreditCardHolderResponse>> find(@PathVariable final UUID cardCode) {
        log.info("m=find, cardCode={}", cardCode);
        return ResponseEntity.ok(mapper.toHolderResponseList(service.findHolders(cardCode)));
    }

    @PostMapping
    public ResponseEntity<CreditCardHolderResponse> create(@PathVariable final UUID cardCode,
                                                            @Valid @RequestBody final CreditCardHolderRequest request) {
        log.info("m=create, cardCode={}, request={}", cardCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(service.createAdditional(cardCode, request.name())));
    }

    @PatchMapping("/{holderCode}")
    public ResponseEntity<CreditCardHolderResponse> update(@PathVariable final UUID cardCode,
                                                            @PathVariable final UUID holderCode,
                                                            @Valid @RequestBody final CreditCardHolderUpdateRequest request) {
        log.info("m=update, cardCode={}, holderCode={}", cardCode, holderCode);
        return ResponseEntity.ok(mapper.toResponse(service.updateHolder(cardCode, holderCode, request.name())));
    }

    @PatchMapping("/{holderCode}/inactivate")
    public ResponseEntity<CreditCardHolderResponse> inactivate(@PathVariable final UUID cardCode,
                                                                @PathVariable final UUID holderCode) {
        return ResponseEntity.ok(mapper.toResponse(service.changeHolderStatus(cardCode, holderCode, CreditCardHolderStatus.INACTIVE)));
    }

    @PatchMapping("/{holderCode}/activate")
    public ResponseEntity<CreditCardHolderResponse> activate(@PathVariable final UUID cardCode,
                                                              @PathVariable final UUID holderCode) {
        return ResponseEntity.ok(mapper.toResponse(service.changeHolderStatus(cardCode, holderCode, CreditCardHolderStatus.ACTIVE)));
    }
}
