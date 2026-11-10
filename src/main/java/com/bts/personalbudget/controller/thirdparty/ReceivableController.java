package com.bts.personalbudget.controller.thirdparty;

import com.bts.personalbudget.controller.thirdparty.config.ReceivableControllerApiDocs;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableService;
import com.bts.personalbudget.mapper.ReceivableMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/third_party/{thirdPartyCode}")
public class ReceivableController implements ReceivableControllerApiDocs {
    private final ReceivableService service;
    private final ReceivableMapper mapper;

    @GetMapping("/receivables")
    public ResponseEntity<List<ReceivableResponse>> find(@PathVariable final UUID thirdPartyCode) {
        return ResponseEntity.ok(mapper.toResponseList(service.find(thirdPartyCode)));
    }

    @PatchMapping("/receivables/{receivableCode}")
    public ResponseEntity<ReceivableResponse> updateDueDate(@PathVariable final UUID thirdPartyCode,
                                                             @PathVariable final UUID receivableCode,
                                                             @Valid @RequestBody final ReceivableUpdateRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.updateDueDate(thirdPartyCode, receivableCode, request.dueDate())));
    }

    @PostMapping("/receipts")
    public ResponseEntity<List<ReceivableResponse>> receive(@PathVariable final UUID thirdPartyCode,
                                                             @Valid @RequestBody final ReceiptRequest request) {
        return ResponseEntity.ok(mapper.toResponseList(service.receive(thirdPartyCode, request.amount(),
                request.effectiveDate(), request.accountCode())));
    }

    @PostMapping("/receivables/{receivableCode}/write_off")
    public ResponseEntity<ReceivableResponse> writeOff(@PathVariable final UUID thirdPartyCode,
                                                        @PathVariable final UUID receivableCode,
                                                        @Valid @RequestBody final ReceivableWriteOffRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.writeOff(thirdPartyCode, receivableCode,
                request.effectiveDate())));
    }
}
