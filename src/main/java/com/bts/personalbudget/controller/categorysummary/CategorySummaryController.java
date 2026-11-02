package com.bts.personalbudget.controller.categorysummary;

import com.bts.personalbudget.controller.categorysummary.config.CategorySummaryControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import com.bts.personalbudget.core.domain.service.category.CategorySummaryService;
import com.bts.personalbudget.mapper.CategorySummaryMapper;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/category_summary")
public class CategorySummaryController implements CategorySummaryControllerApiDocs {

    private final CategorySummaryService categorySummaryService;
    private final CategorySummaryMapper mapper;

    @GetMapping
    @Override
    public ResponseEntity<CategorySummaryResponse> summarize(
            @RequestParam("start_date") final LocalDate startDate,
            @RequestParam("end_date") final LocalDate endDate,
            @RequestParam(value = "operation_type", defaultValue = "DEBIT") final OperationType operationType) {
        log.info("m=summarize, startDate={}, endDate={}, operationType={}", startDate, endDate, operationType);

        final CategorySummary summary = categorySummaryService.summarize(startDate, endDate, operationType);

        return ResponseEntity.ok(mapper.toResponse(summary));
    }

}
