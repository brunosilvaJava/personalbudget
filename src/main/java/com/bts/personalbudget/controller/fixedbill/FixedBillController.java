package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.controller.common.SortFieldResolver;
import com.bts.personalbudget.controller.fixedbill.config.FixedBillControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.mapper.FixedBillMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Fixed Bill", description = "API for managing fixed bills")
@RequiredArgsConstructor
@RequestMapping("/fixed_bill")
@RestController
public class FixedBillController implements FixedBillControllerApiDocs {

    /**
     * sort_by values accepted by GET /fixed_bill, mapped to the entity property.
     */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "description", "description",
            "amount", "amount",
            "operation_type", "operationType",
            "status", "status",
            "recurrence_type", "recurrenceType",
            "start_date", "startDate",
            "next_due_date", "nextDueDate",
            "end_date", "endDate");

    private final FixedBillService fixedBillService;
    private final FixedBillMapper fixedBillMapper;

    @Override
    @PostMapping
    public ResponseEntity<FixedBillResponse> create(@Valid @RequestBody FixedBillRequest fixedBillRequest) {
        log.info("m=create fixedBillRequest={}", fixedBillRequest);
        final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillRequest);
        final FixedBill savedFixedBill = fixedBillService.save(fixedBill);
        return ResponseEntity.status(HttpStatus.CREATED).body(fixedBillMapper.toResponse(savedFixedBill));
    }

    @GetMapping
    @Override
    public ResponseEntity<PagedFixedBillResponse> find(
            @RequestParam(defaultValue = "") String description,
            @RequestParam(value = "operation_type", required = false) List<OperationType> operationTypes,
            @RequestParam(value = "status", required = false) List<FixedBillStatus> statuses,
            @RequestParam(value = "recurrence_type", required = false) List<RecurrenceType> recurrenceTypes,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "sort_by", defaultValue = "description") String sortBy,
            @RequestParam(value = "sort_direction", defaultValue = "ASC") String sortDirection) {

        log.info("m=find, description={}, operationTypes={}, statuses={}, recurrenceTypes={}, page={}, size={}, sortBy={}, sortDirection={}",
                description, operationTypes, statuses, recurrenceTypes, page, size, sortBy, sortDirection);

        Pageable pageable = PageRequest.of(page, size, SortFieldResolver.resolve(sortBy, sortDirection, SORT_FIELDS));

        Page<FixedBill> pagedResult = fixedBillService.find(description, operationTypes, statuses, recurrenceTypes, pageable);

        PagedFixedBillResponse response = new PagedFixedBillResponse(
                fixedBillMapper.toResponseList(pagedResult.getContent()),
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
    public ResponseEntity<FixedBillResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode code={}", code);
        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBillService.findByCode(code)));
    }

    @PatchMapping("/{code}")
    @Override
    public ResponseEntity<FixedBillResponse> update(@PathVariable UUID code,
                                                    @RequestBody @Valid final FixedBillUpdateRequest fixedBillUpdateRequest){
        log.info("m=update code={} fixedBillUpdateRequest={}", code, fixedBillUpdateRequest);
        final FixedBill fixedBill = fixedBillService.update(
                code,
                fixedBillUpdateRequest.operationType(),
                fixedBillUpdateRequest.description(),
                fixedBillUpdateRequest.amount(),
                fixedBillUpdateRequest.recurrenceType(),
                fixedBillUpdateRequest.days(),
                fixedBillUpdateRequest.referenceYear(),
                fixedBillUpdateRequest.status(),
                fixedBillUpdateRequest.startDate(),
                fixedBillUpdateRequest.endDate()
        );

        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBill));
    }

    @PatchMapping("/{code}/inactivate")
    @Override
    public ResponseEntity<FixedBillResponse> inactivate(@PathVariable UUID code) {
        log.info("m=inactivate code={}", code);
        final FixedBill fixedBill = fixedBillService.changeStatus(code, FixedBillStatus.INACTIVE);
        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBill));
    }

    @PatchMapping("/{code}/activate")
    @Override
    public ResponseEntity<FixedBillResponse> activate(@PathVariable UUID code) {
        log.info("m=activate code={}", code);
        final FixedBill fixedBill = fixedBillService.changeStatus(code, FixedBillStatus.ACTIVE);
        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBill));
    }

    @DeleteMapping("/{code}")
    @Override
    public ResponseEntity<Void> deleteById(@PathVariable UUID code) {
        log.info("m=deleteById code={}", code);
        fixedBillService.delete(code);
        return ResponseEntity.noContent().build();
    }
}