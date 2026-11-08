package com.bts.personalbudget.controller.movementsettlement;

import com.bts.personalbudget.controller.movementsettlement.config.MovementSettlementControllerApiDocs;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.mapper.MovementSettlementMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Movement settlement", description = "API for recording and reversing payments")
@RequiredArgsConstructor
@RequestMapping("/financial_movement/{movementCode}/settlements")
@RestController
public class MovementSettlementController implements MovementSettlementControllerApiDocs {

    private final MovementSettlementService service;
    private final MovementSettlementMapper mapper;

    @PostMapping
    @Override
    public ResponseEntity<MovementSettlementResponse> create(
            @PathVariable final UUID movementCode,
            @RequestBody @Valid final MovementSettlementRequest request) {
        log.info("m=create, movementCode={}, request={}", movementCode, request);
        final MovementSettlement settlement = service.create(movementCode, mapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(settlement));
    }

    @GetMapping
    @Override
    public ResponseEntity<List<MovementSettlementResponse>> list(@PathVariable final UUID movementCode) {
        log.info("m=list, movementCode={}", movementCode);
        return ResponseEntity.ok(mapper.toResponseList(service.findAll(movementCode)));
    }

    @PostMapping("/{settlementCode}/reversal")
    @Override
    public ResponseEntity<MovementSettlementResponse> reverse(
            @PathVariable final UUID movementCode, @PathVariable final UUID settlementCode) {
        log.info("m=reverse, movementCode={}, settlementCode={}", movementCode, settlementCode);
        return ResponseEntity.ok(mapper.toResponse(service.reverse(movementCode, settlementCode)));
    }
}
