package com.bts.personalbudget.controller.incomeschedule;

import com.bts.personalbudget.controller.incomeschedule.config.IncomeScheduleControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.income.MaterializationResult;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Income schedule", description = "API for managing the main income and its occurrences")
@RequiredArgsConstructor
@RequestMapping("/income_schedule")
@RestController
public class IncomeScheduleController implements IncomeScheduleControllerApiDocs {

    private final IncomeScheduleService incomeScheduleService;
    private final IncomeScheduleMapper incomeScheduleMapper;

    @Override
    @PostMapping
    public ResponseEntity<IncomeScheduleResponse> create(@Valid @RequestBody IncomeScheduleRequest request) {
        log.info("m=create, request={}", request);
        final IncomeSchedule schedule = incomeScheduleService.create(incomeScheduleMapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(incomeScheduleMapper.toResponse(schedule));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<IncomeScheduleResponse>> find(
            @RequestParam(value = "status", required = false) List<IncomeScheduleStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        return ResponseEntity.ok(incomeScheduleMapper.toResponseList(incomeScheduleService.find(statuses)));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<IncomeScheduleResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(incomeScheduleMapper.toResponse(incomeScheduleService.findByCode(code)));
    }

    @Override
    @PatchMapping("/{code}")
    public ResponseEntity<IncomeScheduleResponse> update(@PathVariable UUID code,
                                                         @RequestBody @Valid IncomeScheduleUpdateRequest request) {
        log.info("m=update, code={}, request={}", code, request);
        final IncomeSchedule schedule = incomeScheduleService.update(code, incomeScheduleMapper.toUpdate(request));
        return ResponseEntity.ok(incomeScheduleMapper.toResponse(schedule));
    }

    @Override
    @PatchMapping("/{code}/inactivate")
    public ResponseEntity<IncomeScheduleResponse> inactivate(@PathVariable UUID code) {
        log.info("m=inactivate, code={}", code);
        return ResponseEntity.ok(incomeScheduleMapper.toResponse(incomeScheduleService.inactivate(code)));
    }

    @Override
    @PatchMapping("/{code}/activate")
    public ResponseEntity<IncomeScheduleResponse> activate(@PathVariable UUID code) {
        log.info("m=activate, code={}", code);
        return ResponseEntity.ok(incomeScheduleMapper.toResponse(incomeScheduleService.activate(code)));
    }

    @Override
    @GetMapping("/{code}/occurrence")
    public ResponseEntity<List<IncomeOccurrenceResponse>> findOccurrences(
            @PathVariable UUID code,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        log.info("m=findOccurrences, code={}, from={}, to={}", code, from, to);
        return ResponseEntity.ok(incomeScheduleMapper.toOccurrenceResponseList(
                incomeScheduleService.findOccurrences(code, parseMonth("from", from), parseMonth("to", to))));
    }

    @Override
    @PostMapping("/{code}/occurrence/{month}/materialize")
    public ResponseEntity<IncomeOccurrenceResponse> materialize(@PathVariable UUID code,
                                                                @PathVariable String month) {
        log.info("m=materialize, code={}, month={}", code, month);
        final MaterializationResult result = incomeScheduleService.materialize(code, parseMonth("month", month));
        final IncomeOccurrenceResponse body = incomeScheduleMapper.toResponse(result.occurrence());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    /**
     * @return null when the text is null, so the optional parameters keep their defaults
     * @throws InvalidFieldsException (400) when the text is not a yyyy-MM month
     */
    private YearMonth parseMonth(final String field, final String value) {
        if (value == null) {
            return null;
        }
        try {
            return YearMonth.parse(value);
        } catch (final DateTimeParseException ex) {
            throw new InvalidFieldsException("Parâmetros inválidos", IncomeScheduleController.class,
                    Map.of(field, "formato esperado: yyyy-MM"));
        }
    }

}
