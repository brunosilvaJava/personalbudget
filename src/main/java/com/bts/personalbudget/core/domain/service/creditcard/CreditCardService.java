package com.bts.personalbudget.core.domain.service.creditcard;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.model.CreditCard;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CreditCardMapper;
import com.bts.personalbudget.repository.AccountRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration of credit cards (E9a). Purchases, invoices and the limit in use come with E9b.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CreditCardService {

    static final int NAME_MIN_LENGTH = 3;
    static final int NAME_MAX_LENGTH = 50;
    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final CreditCardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final CreditCardMapper mapper;

    @Transactional
    public CreditCard create(final CreditCard card) {
        log.info("m=create, card={}", card);
        final String name = normalizeName(card.name());
        if (cardRepository.existsByName(name)) {
            throw invalid("name", "já existe cartão com esse nome");
        }
        requireDay("closingDay", card.closingDay());
        requireDay("dueDay", card.dueDay());
        requireValidLimit(card.creditLimit());

        final CreditCardEntity entity = mapper.toEntity(card);
        entity.setName(name);
        entity.setDefaultAccount(resolveAccount(card.defaultAccountCode()));
        return mapper.toModel(cardRepository.save(entity));
    }

    /**
     * @param statuses when null or empty only ACTIVE cards are returned.
     */
    @Transactional(readOnly = true)
    public List<CreditCard> find(final List<CreditCardStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        final List<CreditCardStatus> effective =
                statuses == null || statuses.isEmpty() ? List.of(CreditCardStatus.ACTIVE) : statuses;
        return mapper.toModelList(cardRepository.findByStatuses(effective));
    }

    /** Also returns INACTIVE cards: past purchases keep pointing at them. */
    @Transactional(readOnly = true)
    public CreditCard findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return mapper.toModel(findEntity(code));
    }

    /**
     * Fields left null are kept. The limit and the default account can be replaced but not cleared
     * yet. New closing and due days only apply to purchases made afterwards.
     */
    @Transactional
    public CreditCard update(final UUID code, final String name, final Integer closingDay, final Integer dueDay,
                             final BigDecimal creditLimit, final UUID defaultAccountCode) {
        log.info("m=update, code={}, name={}, closingDay={}, dueDay={}, creditLimit={}, defaultAccountCode={}",
                code, name, closingDay, dueDay, creditLimit, defaultAccountCode);
        final CreditCardEntity entity = findEntity(code);
        boolean hasChanges = false;

        if (name != null) {
            final String normalized = normalizeName(name);
            if (!normalized.equals(entity.getName())) {
                if (cardRepository.existsByNameAndCodeNot(normalized, code)) {
                    throw invalid("name", "já existe cartão com esse nome");
                }
                entity.setName(normalized);
                hasChanges = true;
            }
        }
        if (closingDay != null) {
            requireDay("closingDay", closingDay);
            if (closingDay != entity.getClosingDay()) {
                entity.setClosingDay(closingDay);
                hasChanges = true;
            }
        }
        if (dueDay != null) {
            requireDay("dueDay", dueDay);
            if (dueDay != entity.getDueDay()) {
                entity.setDueDay(dueDay);
                hasChanges = true;
            }
        }
        if (creditLimit != null) {
            requireValidLimit(creditLimit);
            if (entity.getCreditLimit() == null || creditLimit.compareTo(entity.getCreditLimit()) != 0) {
                entity.setCreditLimit(creditLimit);
                hasChanges = true;
            }
        }
        if (defaultAccountCode != null) {
            final boolean same = entity.getDefaultAccount() != null
                    && defaultAccountCode.equals(entity.getDefaultAccount().getCode());
            if (!same) {
                entity.setDefaultAccount(resolveAccount(defaultAccountCode));
                hasChanges = true;
            }
        }

        if (!hasChanges) {
            log.info("m=update, message=NoChangesDetected, code={}", code);
            return mapper.toModel(entity);
        }
        return mapper.toModel(cardRepository.save(entity));
    }

    @Transactional
    public CreditCard inactivate(final UUID code) {
        log.info("m=inactivate, code={}", code);
        return changeStatus(code, CreditCardStatus.INACTIVE);
    }

    @Transactional
    public CreditCard activate(final UUID code) {
        log.info("m=activate, code={}", code);
        return changeStatus(code, CreditCardStatus.ACTIVE);
    }

    private CreditCard changeStatus(final UUID code, final CreditCardStatus status) {
        final CreditCardEntity entity = findEntity(code);
        if (entity.getStatus() == status) {
            return mapper.toModel(entity);
        }
        entity.setStatus(status);
        return mapper.toModel(cardRepository.save(entity));
    }

    /** The default account is optional; when informed it must exist and be ACTIVE. */
    private AccountEntity resolveAccount(final UUID accountCode) {
        if (accountCode == null) {
            return null;
        }
        final AccountEntity account = accountRepository.findByCode(accountCode)
                .orElseThrow(() -> invalid("defaultAccountCode", "conta não encontrada"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalid("defaultAccountCode", "conta inativa");
        }
        return account;
    }

    private CreditCardEntity findEntity(final UUID code) {
        return cardRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("CreditCardEntity not found for code " + code));
    }

    private String normalizeName(final String name) {
        final String normalized = name == null ? "" : name.trim();
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw invalid("name", "deve ter entre " + NAME_MIN_LENGTH + " e " + NAME_MAX_LENGTH + " caracteres");
        }
        return normalized;
    }

    private void requireDay(final String field, final Integer day) {
        if (day == null || day < 1 || day > 31) {
            throw invalid(field, "deve ser um dia do mês entre 1 e 31");
        }
    }

    private void requireValidLimit(final BigDecimal creditLimit) {
        if (creditLimit != null && creditLimit.signum() <= 0) {
            throw invalid("creditLimit", "deve ser maior que zero");
        }
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, CreditCardService.class, Map.of(field, message));
    }
}
