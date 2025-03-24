package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.controller.fixedbill.config.FixedBillControllerApiDocs;
import com.bts.personalbudget.controller.installmentbill.FixedBillResponse;
import com.bts.personalbudget.controller.installmentbill.FixedBillUpdateRequest;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.mapper.FixedBillMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Fixed Bill", description = "API for managing fixed bills")
@RequiredArgsConstructor
@RequestMapping("/fixed_bill")
@RestController
public class FixedBillController implements FixedBillControllerApiDocs {

    private final FixedBillService fixedBillService;
    private final FixedBillMapper fixedBillMapper;

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody FixedBillRequest fixedBillRequest) {
        log.info("m=create fixedBillRequest={}", fixedBillRequest);
        final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillRequest);
        fixedBillService.save(fixedBill);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<FixedBillResponse>> findAll() {
        log.info("m=findAll");
        return ResponseEntity.ok(fixedBillMapper.toResponseList(fixedBillService.findAll()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<FixedBillResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode code={}", code);
        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBillService.findByCode(code)));
    }

    @PatchMapping("/{code}")
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
                fixedBillUpdateRequest.flgLeapYear(),
                fixedBillUpdateRequest.status(),
                fixedBillUpdateRequest.startDate(),
                fixedBillUpdateRequest.endDate()
        );

        return ResponseEntity.ok(fixedBillMapper.toResponse(fixedBill));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteById(@PathVariable UUID code) {
        log.info("m=deleteById code={}", code);
        fixedBillService.delete(code);
        return ResponseEntity.noContent().build();
    }

}