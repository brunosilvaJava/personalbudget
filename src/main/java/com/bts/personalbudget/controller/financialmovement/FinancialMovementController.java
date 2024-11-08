package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.controller.financialmovement.config.FinancialMovementControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/financial_movement")
@RestController
public class FinancialMovementController implements FinancialMovementControllerApiDocs {

    private final FinancialMovementService service;
    private final FinancialMovementMapper mapper;

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid final FinancialMovementRequest request) {
        log.info("m=create, request={}", request);
        service.create(mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<FinancialMovementResponse>> find(@RequestParam(defaultValue = "")
                                                                String description,
                                                                @RequestParam(value = "operation_type", required = false)
                                                                List<OperationType> operationTypes,
                                                                @RequestParam(value = "status", required = false)
                                                                List<FinancialMovementStatus> statuses,
                                                                @RequestParam("start_date")
                                                                LocalDate startDate,
                                                                @RequestParam("end_date")
                                                                LocalDate endDate) {
        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}",
                description, operationTypes, statuses, startDate, endDate);
        return ResponseEntity.ok(mapper.toResponse(service.find(description, operationTypes, statuses, startDate, endDate)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<FinancialMovementResponse> find(@PathVariable UUID code) throws NotFoundException {
        log.info("m=find, code={}", code);
        FinancialMovement financialMovement = service.find(code);
        FinancialMovementResponse response = mapper.toResponse(financialMovement);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{code}")
    public ResponseEntity<?> update(@PathVariable UUID code,
                                    @RequestBody @Valid final FinancialMovementUpdateRequest updateRequest) throws NotFoundException {
        log.info("m=update, updateRequest={}", updateRequest);
        return ResponseEntity.ok(mapper.toResponse(service.update(mapper.toModel(updateRequest, code))));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable UUID code) throws NotFoundException {
        log.info("m=delete, code={}", code);
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
