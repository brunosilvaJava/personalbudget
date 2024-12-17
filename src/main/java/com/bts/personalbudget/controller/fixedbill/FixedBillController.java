package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.controller.fixedbill.config.FixedBillControllerApiDocs;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.mapper.FixedBillMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fixed Bill", description = "API for managing fixed bills")
@RequiredArgsConstructor
@RequestMapping("/fixed_bill")
@RestController
public class FixedBillController implements FixedBillControllerApiDocs {

    private final FixedBillService fixedBillService;
    private final FixedBillMapper fixedBillMapper;

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody FixedBillRequest fixedBillRequest) {
        final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillRequest);
        fixedBillService.save(fixedBill);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<FixedBill>> findAll() {
        return ResponseEntity.ok(fixedBillService.findAll());
    }

    @GetMapping("/{code}")
    public ResponseEntity<FixedBill> findByCode(@PathVariable UUID code) {
        return ResponseEntity.ok(fixedBillService.findByCode(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteById(@PathVariable UUID code) {
        fixedBillService.delete(code);
        return ResponseEntity.noContent().build();
    }

}