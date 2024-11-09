package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.controller.fixedbill.config.FixedBillControllerApiDocs;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.mapper.FixedBillMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

}
