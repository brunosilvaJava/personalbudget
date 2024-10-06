package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/installment_bill")
@RestController
public class InstallmentBillController {

    private final InstallmentBillService installmentBillService;
    private final InstallmentBillMapper mapper;

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid final InstallmentBillRequest installmentBillRequest) {
        log.info("m=create installmentBillRequest={}", installmentBillRequest);
        installmentBillService.create(mapper.toModel(installmentBillRequest));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<InstallmentBillResponse>> findAll() {
        log.info("m=findAll");
        final List<InstallmentBill> installmentBillList = installmentBillService.findAll();
        return ResponseEntity.ok(mapper.toResponse(installmentBillList));
    }

    @GetMapping("/{code}")
    public ResponseEntity<InstallmentBillResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode code={}", code);
        final InstallmentBill installmentBill = installmentBillService.findByCode(code);
        return ResponseEntity.ok(mapper.toResponse(installmentBill));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<InstallmentBillResponse> update(@PathVariable UUID code,
                                                          @RequestBody @Valid final InstallmentBillUpdateRequest installmentBillUpdateRequest) {
        log.info("m=update code={}", code);
        final InstallmentBill installmentBill = installmentBillService.update(
                code,
                installmentBillUpdateRequest.operationType(),
                installmentBillUpdateRequest.description(),
                installmentBillUpdateRequest.amount(),
                installmentBillUpdateRequest.purchaseDate(),
                installmentBillUpdateRequest.installmentTotal()
        );
        return ResponseEntity.ok(mapper.toResponse(installmentBill));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable final UUID code) {
        log.info("m=delete code={}", code);
        installmentBillService.delete(code);
        return ResponseEntity.ok().build();
    }

}
