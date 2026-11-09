package com.bts.personalbudget.controller.budgetenvelope;

import com.bts.personalbudget.controller.budgetenvelope.config.BudgetEnvelopeControllerApiDocs;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelopeService;
import com.bts.personalbudget.mapper.BudgetEnvelopeMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Budget envelope", description = "API for planning amounts per category in a pay cycle")
@RequiredArgsConstructor
@RestController
public class BudgetEnvelopeController implements BudgetEnvelopeControllerApiDocs {

    private final BudgetEnvelopeService service;
    private final BudgetEnvelopeMapper mapper;

    @Override
    @PostMapping("/pay_cycle/{cycleCode}/envelope")
    public ResponseEntity<BudgetEnvelopeResponse> create(
            @PathVariable final UUID cycleCode, @RequestBody @Valid final BudgetEnvelopeRequest request) {
        log.info("m=create, cycleCode={}, request={}", cycleCode, request);
        final BudgetEnvelopeResponse response = mapper.toResponse(
                service.create(cycleCode, request.categoryCode(), request.plannedAmount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/pay_cycle/{cycleCode}/envelope")
    public ResponseEntity<CycleEnvelopesResponse> find(@PathVariable final UUID cycleCode) {
        log.info("m=find, cycleCode={}", cycleCode);
        return ResponseEntity.ok(mapper.toResponse(service.find(cycleCode)));
    }

    @Override
    @PatchMapping("/envelope/{code}")
    public ResponseEntity<BudgetEnvelopeResponse> update(
            @PathVariable final UUID code, @RequestBody @Valid final BudgetEnvelopeUpdateRequest request) {
        log.info("m=update, code={}, request={}", code, request);
        return ResponseEntity.ok(mapper.toResponse(service.update(code, request.plannedAmount())));
    }

    @Override
    @DeleteMapping("/envelope/{code}")
    public ResponseEntity<Void> delete(@PathVariable final UUID code) {
        log.info("m=delete, code={}", code);
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
