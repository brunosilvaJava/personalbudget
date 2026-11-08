package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.controller.common.SortFieldResolver;
import com.bts.personalbudget.controller.financialmovement.config.FinancialMovementControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/financial_movement")
@RestController
public class FinancialMovementController implements FinancialMovementControllerApiDocs {

    /**
     * sort_by values accepted by GET /financial_movement, mapped to the entity property.
     */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "description", "description",
            "amount", "amount",
            "operation_type", "operationType",
            "status", "status",
            "movement_date", "movementDate",
            "due_date", "dueDate");

    private final FinancialMovementService service;
    private final FinancialMovementMapper mapper;

    @PostMapping
    @Override
    public ResponseEntity<FinancialMovementResponse> create(@RequestBody @Valid final FinancialMovementRequest request) {
        log.info("m=create, request={}", request);
        final FinancialMovement created = service.create(mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @GetMapping
    @Override
    public ResponseEntity<PagedFinancialMovementResponse> find(
            @RequestParam(defaultValue = "") String description,
            @RequestParam(value = "operation_type", required = false) List<OperationType> operationTypes,
            @RequestParam(value = "status", required = false) List<FinancialMovementStatus> statuses,
            @RequestParam("start_date") LocalDate startDate,
            @RequestParam("end_date") LocalDate endDate,
            @RequestParam(value = "category_code", required = false) List<UUID> categoryCodes,
            @RequestParam(value = "without_category", defaultValue = "false") boolean withoutCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "sort_by", defaultValue = "movement_date") String sortBy,
            @RequestParam(value = "sort_direction", defaultValue = "DESC") String sortDirection) {

        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}, categoryCodes={}, withoutCategory={}, page={}, size={}, sortBy={}, sortDirection={}",
                description, operationTypes, statuses, startDate, endDate, categoryCodes, withoutCategory, page, size, sortBy, sortDirection);

        Pageable pageable = PageRequest.of(page, size, SortFieldResolver.resolve(sortBy, sortDirection, SORT_FIELDS));

        Page<FinancialMovement> pagedResult = service.find(
                description, operationTypes, statuses, startDate, endDate, categoryCodes, withoutCategory, pageable);

        PagedFinancialMovementResponse response = new PagedFinancialMovementResponse(
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
    @Override
    public ResponseEntity<FinancialMovementResponse> find(@PathVariable UUID code) {
        log.info("m=find, code={}", code);
        FinancialMovement financialMovement = service.find(code);
        FinancialMovementResponse response = mapper.toResponse(financialMovement);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{code}")
    @Override
    public ResponseEntity<?> update(@PathVariable UUID code,
                                    @RequestBody @Valid final FinancialMovementUpdateRequest updateRequest) {
        log.info("m=update, updateRequest={}", updateRequest);
        return ResponseEntity.ok(mapper.toResponse(service.update(mapper.toUpdate(updateRequest, code))));
    }

    @DeleteMapping("/{code}")
    @Override
    public ResponseEntity<Void> delete(@PathVariable UUID code) {
        log.info("m=delete, code={}", code);
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}