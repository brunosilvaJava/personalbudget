package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.controller.paycycle.config.PayCycleConfigControllerApiDocs;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.mapper.PayCycleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Pay cycle configuration", description = "API for turning the configurable financial cycle on and off")
@RequiredArgsConstructor
@RequestMapping("/pay_cycle_config")
@RestController
public class PayCycleConfigController implements PayCycleConfigControllerApiDocs {

    private final PayCycleService payCycleService;
    private final PayCycleMapper payCycleMapper;

    @Override
    @GetMapping
    public ResponseEntity<PayCycleConfigResponse> find() {
        log.info("m=find");
        return ResponseEntity.ok(payCycleMapper.toResponse(payCycleService.findConfig()));
    }

    @Override
    @PostMapping("/activation/preview")
    public ResponseEntity<PayCycleActivationPreviewResponse> previewActivation(
            @Valid @RequestBody PayCycleActivationPreviewRequest request) {
        log.info("m=previewActivation, request={}", request);
        return ResponseEntity.ok(payCycleMapper.toResponse(payCycleService.previewActivation(request.initialDate())));
    }

    @Override
    @PostMapping("/activation")
    public ResponseEntity<PayCycleConfigResponse> activate(@Valid @RequestBody PayCycleActivationRequest request) {
        log.info("m=activate, request={}", request);
        return ResponseEntity.ok(payCycleMapper.toResponse(
                payCycleService.activate(request.initialDate(), request.expectedVersion())));
    }

    @Override
    @PostMapping("/deactivation")
    public ResponseEntity<PayCycleConfigResponse> deactivate(@Valid @RequestBody PayCycleDeactivationRequest request) {
        log.info("m=deactivate, request={}", request);
        return ResponseEntity.ok(payCycleMapper.toResponse(
                payCycleService.deactivate(request.expectedVersion(), request.justification())));
    }

    @Override
    @PostMapping("/anchor_transition/preview")
    public ResponseEntity<PayCycleAnchorTransitionPreviewResponse> previewAnchorTransition(
            @Valid @RequestBody PayCycleAnchorTransitionPreviewRequest request) {
        log.info("m=previewAnchorTransition, request={}", request);
        return ResponseEntity.ok(payCycleMapper.toResponse(
                payCycleService.previewAnchorTransition(payCycleMapper.toModel(request))));
    }

    @Override
    @PostMapping("/anchor_transition")
    public ResponseEntity<PayCycleAnchorTransitionResponse> transitionAnchor(
            @Valid @RequestBody PayCycleAnchorTransitionRequest request) {
        log.info("m=transitionAnchor, request={}", request);
        return ResponseEntity.ok(payCycleMapper.toResponse(
                payCycleService.transitionAnchor(payCycleMapper.toModel(request))));
    }

}
