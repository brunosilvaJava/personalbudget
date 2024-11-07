package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Installment Bill", description = "API for managing installment bills")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/installment_bill")
@RestController
public class InstallmentBillController {

    private final InstallmentBillService installmentBillService;
    private final InstallmentBillMapper mapper;

    @Operation(summary = "Create a new installment bill",
            description = "Creates a new installment bill with the given details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Installment bill successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid final InstallmentBillRequest installmentBillRequest) {
        log.info("m=create installmentBillRequest={}", installmentBillRequest);
        installmentBillService.create(mapper.toModel(installmentBillRequest));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Retrieve all installment bills",
            description = "Fetches a list of all installment bills.")
    @ApiResponse(responseCode = "200", description = "List of installment bills")
    @GetMapping
    public ResponseEntity<List<InstallmentBillResponse>> findAll() {
        log.info("m=findAll");
        final List<InstallmentBill> installmentBillList = installmentBillService.findAll();
        return ResponseEntity.ok(mapper.toResponse(installmentBillList));
    }

    @Operation(summary = "Retrieve an installment bill by code",
            description = "Fetches a specific installment bill by its unique identifier (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Installment bill found and returned"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
    @GetMapping("/{code}")
    public ResponseEntity<InstallmentBillResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode code={}", code);
        final InstallmentBill installmentBill = installmentBillService.findByCode(code);
        return ResponseEntity.ok(mapper.toResponse(installmentBill));
    }

    @Operation(summary = "Update an existing installment bill",
            description = "Updates the details of an existing installment bill identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Installment bill successfully updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
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

    @Operation(summary = "Delete an installment bill by code",
            description = "Deletes an existing installment bill identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Installment bill successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable final UUID code) {
        log.info("m=delete code={}", code);
        installmentBillService.delete(code);
        return ResponseEntity.ok().build();
    }

}
