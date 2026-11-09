package com.bts.personalbudget.controller.accounttransfer;

import com.bts.personalbudget.controller.accounttransfer.config.AccountTransferControllerApiDocs;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransfer;
import com.bts.personalbudget.core.domain.service.accounttransfer.AccountTransferService;
import com.bts.personalbudget.mapper.AccountTransferMapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Account transfer", description = "API for transferring money between accounts")
@RequiredArgsConstructor
@RequestMapping("/account_transfer")
@RestController
public class AccountTransferController implements AccountTransferControllerApiDocs {

    private final AccountTransferService service;
    private final AccountTransferMapper mapper;

    @Override
    @PostMapping
    public ResponseEntity<AccountTransferResponse> create(@Valid @RequestBody final AccountTransferRequest request) {
        log.info("m=create, request={}", request);
        final AccountTransfer transfer = service.create(mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(transfer));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<AccountTransferResponse>> find(
            @RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate,
            @RequestParam(value = "account_code", required = false) final UUID accountCode) {
        log.info("m=find, startDate={}, endDate={}, accountCode={}", startDate, endDate, accountCode);
        return ResponseEntity.ok(mapper.toResponseList(service.find(startDate, endDate, accountCode)));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<AccountTransferResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.findByCode(code)));
    }

    @Override
    @PostMapping("/{code}/reversal")
    public ResponseEntity<AccountTransferResponse> reverse(@PathVariable final UUID code) {
        log.info("m=reverse, code={}", code);
        return ResponseEntity.ok(mapper.toResponse(service.reverse(code)));
    }
}
