package com.bts.personalbudget.controller.thirdparty;

import com.bts.personalbudget.controller.thirdparty.config.ThirdPartyControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.core.domain.service.thirdparty.ThirdPartyService;
import com.bts.personalbudget.mapper.ThirdPartyMapper;
import jakarta.validation.Valid;
import java.util.List;
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
@RequiredArgsConstructor
@RestController
@RequestMapping("/third_party")
public class ThirdPartyController implements ThirdPartyControllerApiDocs {
    private final ThirdPartyService service;
    private final ThirdPartyMapper mapper;

    @PostMapping
    public ResponseEntity<ThirdPartyResponse> create(@Valid @RequestBody final ThirdPartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(service.create(request.name())));
    }
    @GetMapping
    public ResponseEntity<List<ThirdPartyResponse>> find(@RequestParam(value = "status", required = false) final List<ThirdPartyStatus> status) {
        return ResponseEntity.ok(mapper.toResponseList(service.find(status)));
    }
    @GetMapping("/{code}")
    public ResponseEntity<ThirdPartyResponse> findByCode(@PathVariable final UUID code) {
        return ResponseEntity.ok(mapper.toResponse(service.findByCode(code)));
    }
    @PatchMapping("/{code}")
    public ResponseEntity<ThirdPartyResponse> update(@PathVariable final UUID code, @Valid @RequestBody final ThirdPartyUpdateRequest request) {
        return ResponseEntity.ok(mapper.toResponse(service.update(code, request.name())));
    }
    @PatchMapping("/{code}/activate")
    public ResponseEntity<ThirdPartyResponse> activate(@PathVariable final UUID code) {
        return ResponseEntity.ok(mapper.toResponse(service.changeStatus(code, ThirdPartyStatus.ACTIVE)));
    }
    @PatchMapping("/{code}/inactivate")
    public ResponseEntity<ThirdPartyResponse> inactivate(@PathVariable final UUID code) {
        return ResponseEntity.ok(mapper.toResponse(service.changeStatus(code, ThirdPartyStatus.INACTIVE)));
    }
}
