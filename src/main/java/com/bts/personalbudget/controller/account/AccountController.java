package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.controller.account.config.AccountControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.mapper.AccountMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
@Tag(name = "Account", description = "API for managing accounts and wallets")
@RequiredArgsConstructor
@RequestMapping("/account")
@RestController
public class AccountController implements AccountControllerApiDocs {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @Override
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody final AccountRequest request) {
        log.info("m=create, request={}", request);
        final Account account = accountService.create(accountMapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<AccountResponse>> find(
            @RequestParam(value = "status", required = false) final List<AccountStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        return ResponseEntity.ok(accountMapper.toResponseList(accountService.find(statuses)));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<AccountResponse> findByCode(@PathVariable final UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(accountMapper.toResponse(accountService.findByCode(code)));
    }

    @Override
    @PatchMapping("/{code}")
    public ResponseEntity<AccountResponse> update(@PathVariable final UUID code,
                                                  @RequestBody @Valid final AccountUpdateRequest request) {
        log.info("m=update, code={}, request={}", code, request);
        final Account account = accountService.update(code, request.name(), request.type(),
                request.initialBalance(), request.initialBalanceDate(), request.makeDefault());
        return ResponseEntity.ok(accountMapper.toResponse(account));
    }

    @Override
    @PatchMapping("/{code}/inactivate")
    public ResponseEntity<AccountResponse> inactivate(@PathVariable final UUID code) {
        log.info("m=inactivate, code={}", code);
        return ResponseEntity.ok(accountMapper.toResponse(accountService.inactivate(code)));
    }

    @Override
    @PatchMapping("/{code}/activate")
    public ResponseEntity<AccountResponse> activate(@PathVariable final UUID code) {
        log.info("m=activate, code={}", code);
        return ResponseEntity.ok(accountMapper.toResponse(accountService.activate(code)));
    }

    @Override
    @GetMapping("/balance")
    public ResponseEntity<AccountsBalanceResponse> balances(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("m=balances, date={}", date);
        return ResponseEntity.ok(accountMapper.toResponse(accountService.balances(date)));
    }

    @Override
    @GetMapping("/{code}/balance")
    public ResponseEntity<AccountBalanceResponse> balance(
            @PathVariable final UUID code,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("m=balance, code={}, date={}", code, date);
        return ResponseEntity.ok(accountMapper.toResponse(accountService.balance(code, date)));
    }
}
