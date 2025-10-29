package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.controller.installmentbill.config.InstallmentBillControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Installment Bill", description = "API for managing installment bills")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/installment_bill")
@RestController
public class InstallmentBillController implements InstallmentBillControllerApiDocs {

    private final InstallmentBillService installmentBillService;
    private final InstallmentBillMapper mapper;

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid final InstallmentBillRequest installmentBillRequest) {
        log.info("m=create installmentBillRequest={}", installmentBillRequest);
        installmentBillService.create(mapper.toModel(installmentBillRequest));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<PagedInstallmentBillResponse> findAll(
            @RequestParam(defaultValue = "") String description,
            @RequestParam(value = "operation_type", required = false) List<OperationType> operationTypes,
            @RequestParam(value = "status", required = false) List<InstallmentBillStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "purchase_date") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        log.info("m=findAll, description={}, operationTypes={}, statuses={}, page={}, size={}, sortBy={}, sortDirection={}",
                description, operationTypes, statuses, page, size, sortBy, sortDirection);

        String mappedSortBy = mapSortFieldToEntity(sortBy);

        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, mappedSortBy));

        Page<InstallmentBill> pagedResult = installmentBillService.findAll(description, operationTypes, statuses, pageable);

        PagedInstallmentBillResponse response = new PagedInstallmentBillResponse(
                mapper.toResponse(pagedResult.getContent()),
                pagedResult.getNumber(),
                pagedResult.getSize(),
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages(),
                pagedResult.isFirst(),
                pagedResult.isLast()
        );

        return ResponseEntity.ok(response);
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

    private String mapSortFieldToEntity(String field) {
        return switch (field) {
            case "purchase_date" -> "purchaseDate";
            case "operation_type" -> "operationType";
            case "installment_total" -> "installmentTotal";
            case "installment_count" -> "installmentCount";
            case "next_installment_date" -> "nextInstallmentDate";
            case "last_installment_date" -> "lastInstallmentDate";
            default -> field;
        };
    }
}