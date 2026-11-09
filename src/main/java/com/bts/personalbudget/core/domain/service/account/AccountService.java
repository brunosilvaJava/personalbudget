package com.bts.personalbudget.core.domain.service.account;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatementEntryType;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Account;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.AccountMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.AccountTransferRepository;
import com.bts.personalbudget.repository.MovementSettlementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccountService {

    static final int NAME_MIN_LENGTH = 3;
    static final int NAME_MAX_LENGTH = 50;
    static final int STATEMENT_MAX_PERIOD_DAYS = 366;
    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final AccountRepository accountRepository;
    private final MovementSettlementRepository settlementRepository;
    private final AccountTransferRepository transferRepository;
    private final AccountMapper accountMapper;
    private final Clock clock;

    @Transactional
    public Account create(final Account account) {
        log.info("m=create, account={}", account);
        final String name = normalizeName(account.name());
        if (accountRepository.existsByName(name)) {
            throw invalid("name", "já existe conta com esse nome");
        }
        final AccountEntity entity = accountMapper.toEntity(account);
        entity.setName(name);
        entity.setInitialBalance(account.initialBalance() == null ? BigDecimal.ZERO : account.initialBalance());
        entity.setInitialBalanceDate(account.initialBalanceDate() == null
                ? LocalDate.now(clock) : account.initialBalanceDate());
        return accountMapper.toModel(accountRepository.save(entity));
    }

    /**
     * @param statuses when null or empty only ACTIVE accounts are returned, so an inactive account
     *                 leaves the pick lists without any caller having to ask for it.
     */
    @Transactional(readOnly = true)
    public List<Account> find(final List<AccountStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        final List<AccountStatus> effective =
                statuses == null || statuses.isEmpty() ? List.of(AccountStatus.ACTIVE) : statuses;
        return accountMapper.toModelList(accountRepository.findByStatuses(effective));
    }

    /**
     * Also returns INACTIVE accounts: past settlements keep pointing at them.
     */
    @Transactional(readOnly = true)
    public Account findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return accountMapper.toModel(findEntity(code));
    }

    @Transactional
    public Account update(final UUID code, final String name, final AccountType type,
                          final BigDecimal initialBalance, final LocalDate initialBalanceDate,
                          final Boolean makeDefault) {
        log.info("m=update, code={}, name={}, type={}, initialBalance={}, initialBalanceDate={}, makeDefault={}",
                code, name, type, initialBalance, initialBalanceDate, makeDefault);
        if (Boolean.FALSE.equals(makeDefault)) {
            throw invalid("default", "para trocar a conta padrão, marque outra conta como padrão");
        }
        final AccountEntity entity = findEntity(code);
        boolean hasChanges = false;

        if (name != null) {
            final String normalized = normalizeName(name);
            if (!normalized.equals(entity.getName())) {
                if (accountRepository.existsByNameAndCodeNot(normalized, code)) {
                    throw invalid("name", "já existe conta com esse nome");
                }
                entity.setName(normalized);
                hasChanges = true;
            }
        }
        if (type != null && type != entity.getType()) {
            entity.setType(type);
            hasChanges = true;
        }
        if (initialBalance != null && initialBalance.compareTo(entity.getInitialBalance()) != 0) {
            entity.setInitialBalance(initialBalance);
            hasChanges = true;
        }
        if (initialBalanceDate != null && !initialBalanceDate.equals(entity.getInitialBalanceDate())) {
            rejectDateAfterEarliestSettlement(entity, initialBalanceDate);
            entity.setInitialBalanceDate(initialBalanceDate);
            hasChanges = true;
        }
        if (Boolean.TRUE.equals(makeDefault) && !entity.isDefaultAccount()) {
            makeDefault(entity);
            hasChanges = true;
        }

        if (!hasChanges) {
            log.info("m=update, message=NoChangesDetected, code={}", code);
            return accountMapper.toModel(entity);
        }
        return accountMapper.toModel(accountRepository.save(entity));
    }

    @Transactional
    public Account inactivate(final UUID code) {
        log.info("m=inactivate, code={}", code);
        final AccountEntity entity = findEntity(code);
        if (entity.getStatus() == AccountStatus.INACTIVE) {
            return accountMapper.toModel(entity);
        }
        if (entity.isDefaultAccount()) {
            throw new ConflictException(
                    "A conta padrão não pode ser inativada. Defina outra conta como padrão antes.");
        }
        entity.setStatus(AccountStatus.INACTIVE);
        return accountMapper.toModel(accountRepository.save(entity));
    }

    @Transactional
    public Account activate(final UUID code) {
        log.info("m=activate, code={}", code);
        final AccountEntity entity = findEntity(code);
        if (entity.getStatus() == AccountStatus.ACTIVE) {
            return accountMapper.toModel(entity);
        }
        entity.setStatus(AccountStatus.ACTIVE);
        return accountMapper.toModel(accountRepository.save(entity));
    }

    /**
     * Balance on {@code date} (today when null). A date before the initial balance date gives zero:
     * the account did not exist yet.
     */
    @Transactional(readOnly = true)
    public AccountBalance balance(final UUID code, final LocalDate date) {
        log.info("m=balance, code={}, date={}", code, date);
        return balanceOf(findEntity(code), date == null ? LocalDate.now(clock) : date);
    }

    /**
     * Balance of every account (ACTIVE and INACTIVE) on {@code date} (today when null) plus the total.
     */
    @Transactional(readOnly = true)
    public AccountsBalance balances(final LocalDate date) {
        log.info("m=balances, date={}", date);
        final LocalDate effectiveDate = date == null ? LocalDate.now(clock) : date;
        final List<AccountBalance> balances = accountRepository
                .findByStatuses(List.of(AccountStatus.ACTIVE, AccountStatus.INACTIVE)).stream()
                .map(entity -> balanceOf(entity, effectiveDate))
                .toList();
        final BigDecimal total = balances.stream().map(AccountBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountsBalance(effectiveDate, balances, total);
    }

    /**
     * What moved the account from {@code startDate} through {@code endDate} (both inclusive, at most
     * {@value #STATEMENT_MAX_PERIOD_DAYS} days): the initial balance when its date is in the period,
     * the active settlements and the active transfers, oldest first, each with the balance right after
     * it. Reversed settlements and transfers are left out, as in the balance.
     */
    @Transactional(readOnly = true)
    public AccountStatement statement(final UUID code, final LocalDate startDate, final LocalDate endDate) {
        log.info("m=statement, code={}, startDate={}, endDate={}", code, startDate, endDate);
        if (startDate == null || endDate == null) {
            throw invalid(startDate == null ? "startDate" : "endDate", "o período deve ser informado");
        }
        if (endDate.isBefore(startDate)) {
            throw invalid("endDate", "não pode ser anterior à data inicial");
        }
        if (startDate.plusDays(STATEMENT_MAX_PERIOD_DAYS).isBefore(endDate)) {
            throw invalid("endDate", "o período não pode passar de " + STATEMENT_MAX_PERIOD_DAYS + " dias");
        }
        final AccountEntity entity = findEntity(code);
        final BigDecimal opening = balanceOf(entity, startDate.minusDays(1)).balance();
        final LocalDate initialDate = entity.getInitialBalanceDate();
        if (endDate.isBefore(initialDate)) {
            return new AccountStatement(entity.getCode(), startDate, endDate, opening, opening, List.of());
        }
        final LocalDate from = startDate.isBefore(initialDate) ? initialDate : startDate;

        final List<AccountStatementEntry> unsorted = new ArrayList<>();
        if (!initialDate.isBefore(startDate)) {
            unsorted.add(new AccountStatementEntry(AccountStatementEntryType.INITIAL_BALANCE, initialDate,
                    entity.getInitialBalance(), null, "Saldo inicial", null, null, null, null, null, null));
        }
        settlementRepository.findActiveByAccountBetween(entity.getId(), from, endDate)
                .forEach(settlement -> unsorted.add(settlementEntry(settlement)));
        transferRepository.findActiveByAccountBetween(entity.getId(), from, endDate)
                .forEach(transfer -> unsorted.add(transferEntry(entity.getId(), transfer)));

        // Stable sort: on the same day the initial balance comes first, then settlements, then
        // transfers, each in the order the repositories returned (by id).
        unsorted.sort(Comparator.comparing(AccountStatementEntry::date));
        BigDecimal running = opening;
        final List<AccountStatementEntry> entries = new ArrayList<>(unsorted.size());
        for (final AccountStatementEntry entry : unsorted) {
            running = running.add(entry.amount());
            entries.add(new AccountStatementEntry(entry.type(), entry.date(), entry.amount(), running,
                    entry.description(), entry.movementCode(), entry.settlementCode(), entry.categoryCode(),
                    entry.invoicePaymentCode(), entry.transferCode(), entry.counterpartAccountCode()));
        }
        return new AccountStatement(entity.getCode(), startDate, endDate, opening, running, entries);
    }

    private static AccountStatementEntry settlementEntry(final MovementSettlementEntity settlement) {
        final FinancialMovementEntity movement = settlement.getFinancialMovement();
        final BigDecimal amount = settlement.getAmount().abs();
        return new AccountStatementEntry(AccountStatementEntryType.SETTLEMENT, settlement.getEffectiveDate(),
                movement.getOperationType() == OperationType.DEBIT ? amount.negate() : amount, null,
                movement.getDescription(), movement.getCode(), settlement.getCode(), movement.getCategoryCode(),
                settlement.getInvoicePayment() == null ? null : settlement.getInvoicePayment().getCode(),
                null, null);
    }

    private static AccountStatementEntry transferEntry(final Long accountId, final AccountTransferEntity transfer) {
        final boolean incoming = transfer.getToAccount().getId().equals(accountId);
        final AccountEntity counterpart = incoming ? transfer.getFromAccount() : transfer.getToAccount();
        return new AccountStatementEntry(
                incoming ? AccountStatementEntryType.TRANSFER_IN : AccountStatementEntryType.TRANSFER_OUT,
                transfer.getEffectiveDate(), incoming ? transfer.getAmount() : transfer.getAmount().negate(), null,
                transfer.getDescription(), null, null, null, null, transfer.getCode(), counterpart.getCode());
    }

    private AccountBalance balanceOf(final AccountEntity entity, final LocalDate effectiveDate) {
        if (effectiveDate.isBefore(entity.getInitialBalanceDate())) {
            return new AccountBalance(entity.getCode(), effectiveDate,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        final LocalDate from = entity.getInitialBalanceDate();
        final BigDecimal settlements = settlementRepository.sumActiveSignedAmountsByAccountBetween(
                entity.getId(), from, effectiveDate, OperationType.DEBIT);
        final BigDecimal incoming = transferRepository.sumActiveIncomingBetween(entity.getId(), from, effectiveDate);
        final BigDecimal outgoing = transferRepository.sumActiveOutgoingBetween(entity.getId(), from, effectiveDate);
        final BigDecimal transfers = incoming.subtract(outgoing);
        return new AccountBalance(entity.getCode(), effectiveDate, entity.getInitialBalance(), settlements,
                transfers, entity.getInitialBalance().add(settlements).add(transfers));
    }

    private void makeDefault(final AccountEntity entity) {
        if (entity.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("Uma conta inativa não pode ser a conta padrão");
        }
        // The unique slot allows a single default: release the current one before taking it.
        accountRepository.findByDefaultAccountTrue().ifPresent(current -> {
            current.setDefaultAccount(false);
            accountRepository.saveAndFlush(current);
        });
        entity.setDefaultAccount(true);
    }

    private void rejectDateAfterEarliestSettlement(final AccountEntity entity, final LocalDate newDate) {
        final Optional<LocalDate> earliestSettlement =
                settlementRepository.findEarliestActiveEffectiveDateByAccount(entity.getId());
        final Optional<LocalDate> earliestTransfer =
                transferRepository.findEarliestActiveEffectiveDateByAccount(entity.getId());
        Stream.of(earliestSettlement, earliestTransfer)
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder())
                .filter(newDate::isAfter)
                .ifPresent(earliest -> {
                    throw invalid("initialBalanceDate",
                            "não pode ser posterior à primeira liquidação ou transferência da conta (" + earliest + ")");
                });
    }

    private AccountEntity findEntity(final UUID code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("AccountEntity not found for code " + code));
    }

    private String normalizeName(final String name) {
        final String normalized = name == null ? "" : name.trim();
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw invalid("name", "deve ter entre " + NAME_MIN_LENGTH + " e " + NAME_MAX_LENGTH + " caracteres");
        }
        return normalized;
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, AccountService.class, Map.of(field, message));
    }
}
