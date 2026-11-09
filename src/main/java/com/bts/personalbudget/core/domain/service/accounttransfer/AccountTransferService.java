package com.bts.personalbudget.core.domain.service.accounttransfer;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.AccountTransferMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.AccountTransferRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers move money between two accounts. They are not movements: no category, no competence,
 * no effect on cash reports or pay cycles. Only the account balances see them.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AccountTransferService {

    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";
    static final int MAX_PERIOD_DAYS = 366;

    private final AccountTransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final AccountTransferMapper mapper;
    private final Clock clock;

    @Transactional
    public AccountTransfer create(final AccountTransfer transfer) {
        log.info("m=create, transfer={}", transfer);
        if (transfer.amount() == null || transfer.amount().signum() <= 0) {
            throw invalid("amount", "deve ser maior que zero");
        }
        if (transfer.effectiveDate() == null) {
            throw invalid("effectiveDate", "deve ser informada");
        }
        if (transfer.fromAccountCode() == null || transfer.toAccountCode() == null) {
            throw invalid("fromAccountCode", "origem e destino devem ser informados");
        }
        if (transfer.fromAccountCode().equals(transfer.toAccountCode())) {
            throw invalid("toAccountCode", "deve ser diferente da conta de origem");
        }
        final AccountEntity from = resolve("fromAccountCode", transfer.fromAccountCode(), transfer.effectiveDate());
        final AccountEntity to = resolve("toAccountCode", transfer.toAccountCode(), transfer.effectiveDate());

        final AccountTransferEntity entity = mapper.toEntity(transfer);
        entity.setFromAccount(from);
        entity.setToAccount(to);
        entity.setDescription(normalizeDescription(transfer.description()));
        return mapper.toModel(transferRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public AccountTransfer findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return mapper.toModel(transferRepository.findByCode(code).orElseThrow(() -> notFound(code)));
    }

    /**
     * Includes reversed transfers: the history stays visible. Both dates are inclusive.
     *
     * @param accountCode when informed, only transfers where the account is the origin or the destination
     */
    @Transactional(readOnly = true)
    public List<AccountTransfer> find(final LocalDate startDate, final LocalDate endDate, final UUID accountCode) {
        log.info("m=find, startDate={}, endDate={}, accountCode={}", startDate, endDate, accountCode);
        if (startDate == null || endDate == null) {
            throw invalid("startDate", "o período deve ser informado");
        }
        if (endDate.isBefore(startDate)) {
            throw invalid("endDate", "não pode ser anterior à data inicial");
        }
        if (startDate.plusDays(MAX_PERIOD_DAYS).isBefore(endDate)) {
            throw invalid("endDate", "o período não pode passar de " + MAX_PERIOD_DAYS + " dias");
        }
        return mapper.toModelList(transferRepository.findByFilters(startDate, endDate, accountCode));
    }

    @Transactional
    public AccountTransfer reverse(final UUID code) {
        log.info("m=reverse, code={}", code);
        final AccountTransferEntity entity = transferRepository.findForUpdateByCode(code)
                .orElseThrow(() -> notFound(code));
        if (entity.getReversedAt() != null) {
            throw new ConflictException("A transferência já foi estornada");
        }
        entity.reverse(LocalDateTime.now(clock));
        transferRepository.flush();
        return mapper.toModel(entity);
    }

    /**
     * The account must exist, be ACTIVE and already exist on the effective date.
     */
    private AccountEntity resolve(final String field, final UUID accountCode, final LocalDate effectiveDate) {
        final AccountEntity account = accountRepository.findByCode(accountCode)
                .orElseThrow(() -> invalid(field, "conta não encontrada"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalid(field, "conta inativa");
        }
        if (effectiveDate.isBefore(account.getInitialBalanceDate())) {
            throw invalid("effectiveDate", "não pode ser anterior à data do saldo inicial da conta "
                    + account.getName() + " (" + account.getInitialBalanceDate() + ")");
        }
        return account;
    }

    private String normalizeDescription(final String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        final String trimmed = description.trim();
        if (trimmed.length() > 100) {
            throw invalid("description", "deve ter no máximo 100 caracteres");
        }
        return trimmed;
    }

    private EntityNotFoundException notFound(final UUID code) {
        return new EntityNotFoundException("AccountTransferEntity not found for code " + code);
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, AccountTransferService.class, Map.of(field, message));
    }
}
