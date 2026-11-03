package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.controller.paycycle.config.PayCycleControllerApiDocs;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.mapper.PayCycleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Pay cycle", description = "API for reading and adjusting the financial cycles")
@RequiredArgsConstructor
@RequestMapping("/pay_cycle")
@RestController
public class PayCycleController implements PayCycleControllerApiDocs {

    private final PayCycleService payCycleService;
    private final PayCycleMapper payCycleMapper;

    @Override
    @GetMapping
    public ResponseEntity<List<PayCycleResponse>> find(
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "include_cancelled", required = false, defaultValue = "false") boolean includeCancelled) {
        log.info("m=find, date={}, from={}, to={}, includeCancelled={}", date, from, to, includeCancelled);
        return ResponseEntity.ok(payCycleMapper.toResponseList(
                payCycleService.findCycles(date, from, to, includeCancelled)));
    }

    @Override
    @GetMapping("/current")
    public ResponseEntity<?> findCurrent() {
        log.info("m=findCurrent");
        final CurrentPayCycle current = payCycleService.findCurrent();
        if (current.cycle() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(payCycleMapper.toNotFoundResponse(current));
        }
        return ResponseEntity.ok(payCycleMapper.toResponse(current.cycle()));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<PayCycleResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(payCycleMapper.toResponse(payCycleService.findByCode(code)));
    }

    @Override
    @PostMapping("/{code}/end_adjustment/preview")
    public ResponseEntity<PayCycleEndAdjustmentPreviewResponse> previewEndAdjustment(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleEndAdjustmentPreviewRequest request) {
        log.info("m=previewEndAdjustment, code={}, request={}", code, request);
        return ResponseEntity.ok(payCycleMapper.toResponse(
                payCycleService.previewEndAdjustment(code, request.newEndDate())));
    }

    @Override
    @PostMapping("/{code}/end_adjustment")
    public ResponseEntity<PayCycleEndAdjustmentResponse> adjustEnd(
            @PathVariable UUID code,
            @Valid @RequestBody PayCycleEndAdjustmentRequest request) {
        log.info("m=adjustEnd, code={}, request={}", code, request);
        return ResponseEntity.ok(payCycleMapper.toResponse(payCycleService.adjustEnd(
                code,
                request.newEndDate(),
                request.justification(),
                request.expectedVersion(),
                request.expectedNextVersion())));
    }

    @Override
    @GetMapping("/{code}/adjustment")
    public ResponseEntity<List<PayCycleAdjustmentResponse>> findAdjustments(@PathVariable UUID code) {
        log.info("m=findAdjustments, code={}", code);
        return ResponseEntity.ok(payCycleMapper.toAdjustmentResponseList(payCycleService.findAdjustments(code)));
    }

}
