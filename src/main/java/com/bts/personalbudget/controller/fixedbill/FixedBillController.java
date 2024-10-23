package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillServiceFactory;
import com.bts.personalbudget.mapper.FixedBillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/fixed_bill")
@RestController
public class FixedBillController {

    private final FixedBillServiceFactory fixedBillServiceFactory;
    private final FixedBillMapper fixedBillMapper;

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody FixedBillRequest fixedBillRequest) {
        final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillRequest);
        final FixedBillService fixedBillService = fixedBillServiceFactory.build(fixedBill.getRecurrenceType());
        fixedBillService.save(fixedBill);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
