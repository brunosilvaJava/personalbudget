package com.bts.personalbudget.core.domain.service.creditcardholder;

import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardPlasticEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticKind;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.core.domain.service.creditcard.CreditCardService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CreditCardHolderMapper;
import com.bts.personalbudget.repository.CreditCardHolderRepository;
import com.bts.personalbudget.repository.CreditCardPlasticRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CreditCardHolderService {
    private final CreditCardRepository cardRepository;
    private final CreditCardHolderRepository holderRepository;
    private final CreditCardPlasticRepository plasticRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CreditCardHolderMapper mapper;

    @Transactional(readOnly = true)
    public List<CreditCardHolder> findHolders(final UUID cardCode) {
        log.info("m=findHolders, cardCode={}", cardCode);
        return mapper.toHolderModelList(holderRepository.findByCardId(card(cardCode).getId()));
    }

    @Transactional
    public CreditCardHolder createAdditional(final UUID cardCode, final String name) {
        log.info("m=createAdditional, cardCode={}, name={}", cardCode, name);
        final CreditCardEntity card = card(cardCode);
        final String normalized = name(name);
        if (holderRepository.existsByCardIdAndName(card.getId(), normalized)) {
            throw conflict("Já existe titular com esse nome neste cartão");
        }
        return mapper.toModel(holderRepository.save(CreditCardHolderEntity.builder().card(card).name(normalized)
                .kind(CreditCardHolderKind.ADICIONAL).status(CreditCardHolderStatus.ACTIVE).build()));
    }

    @Transactional
    public CreditCardHolder updateHolder(final UUID cardCode, final UUID holderCode, final String name) {
        log.info("m=updateHolder, cardCode={}, holderCode={}, name={}", cardCode, holderCode, name);
        final CreditCardHolderEntity holder = holder(cardCode, holderCode);
        if (name == null) return mapper.toModel(holder);
        final String normalized = name(name);
        if (!normalized.equals(holder.getName())
                && holderRepository.existsByCardIdAndNameAndCodeNot(holder.getCard().getId(), normalized, holderCode)) {
            throw conflict("Já existe titular com esse nome neste cartão");
        }
        holder.setName(normalized);
        return mapper.toModel(holderRepository.save(holder));
    }

    @Transactional
    public CreditCardHolder changeHolderStatus(final UUID cardCode, final UUID holderCode,
                                                final CreditCardHolderStatus status) {
        log.info("m=changeHolderStatus, cardCode={}, holderCode={}, status={}", cardCode, holderCode, status);
        final CreditCardHolderEntity holder = holder(cardCode, holderCode);
        if (holder.getKind() == CreditCardHolderKind.TITULAR && status == CreditCardHolderStatus.INACTIVE) {
            throw conflict("TITULAR não pode ser inativado");
        }
        holder.setStatus(status);
        return mapper.toModel(holderRepository.save(holder));
    }

    @Transactional(readOnly = true)
    public List<CreditCardPlastic> findPlastics(final UUID cardCode) {
        log.info("m=findPlastics, cardCode={}", cardCode);
        return mapper.toPlasticModelList(plasticRepository.findByCardId(card(cardCode).getId()));
    }

    @Transactional
    public CreditCardPlastic createPlastic(final UUID cardCode, final UUID holderCode, final String lastDigits,
                                           final CreditCardPlasticKind kind) {
        log.info("m=createPlastic, cardCode={}, holderCode={}, lastDigits={}, kind={}",
                cardCode, holderCode, lastDigits, kind);
        final CreditCardEntity card = card(cardCode);
        final CreditCardHolderEntity holder = holder(cardCode, holderCode);
        final String normalizedDigits = digits(lastDigits);
        if (plasticRepository.findByCardIdAndLastDigits(card.getId(), normalizedDigits).isPresent()) {
            throw conflict("Final de cartão repetido no mesmo cartão");
        }
        return mapper.toModel(plasticRepository.save(CreditCardPlasticEntity.builder().card(card).holder(holder)
                .lastDigits(normalizedDigits).kind(kind == null ? CreditCardPlasticKind.UNKNOWN : kind)
                .status(CreditCardPlasticStatus.ACTIVE).build()));
    }

    @Transactional
    public CreditCardPlastic updatePlastic(final UUID cardCode, final UUID plasticCode,
                                           final CreditCardPlasticKind kind, final CreditCardPlasticStatus status,
                                           final UUID defaultThirdPartyCode, final boolean clearDefaultThirdParty) {
        log.info("m=updatePlastic, cardCode={}, plasticCode={}, kind={}, status={}, defaultThirdPartyCode={}, clear={}",
                cardCode, plasticCode, kind, status, defaultThirdPartyCode, clearDefaultThirdParty);
        if (clearDefaultThirdParty && defaultThirdPartyCode != null) {
            throw invalid("defaultThirdPartyCode", "não pode ser informado junto com clearDefaultThirdParty");
        }
        final CreditCardPlasticEntity plastic = plastic(plasticCode, cardCode);
        if (kind != null) plastic.setKind(kind);
        if (status != null) plastic.setStatus(status);
        if (clearDefaultThirdParty) plastic.setDefaultThirdParty(null);
        if (defaultThirdPartyCode != null) plastic.setDefaultThirdParty(activeThirdParty(defaultThirdPartyCode));
        return mapper.toModel(plasticRepository.save(plastic));
    }

    private CreditCardEntity card(final UUID code) {
        return cardRepository.findByCode(code).orElseThrow(() -> new EntityNotFoundException(
                "CreditCardEntity not found for code " + code));
    }

    private CreditCardHolderEntity holder(final UUID cardCode, final UUID holderCode) {
        final CreditCardHolderEntity holder = holderRepository.findByCode(holderCode).orElseThrow(() ->
                new EntityNotFoundException("CreditCardHolderEntity not found for code " + holderCode));
        if (!holder.getCard().getCode().equals(cardCode)) throw conflict("O titular não pertence a este cartão");
        return holder;
    }

    private CreditCardPlasticEntity plastic(final UUID plasticCode, final UUID cardCode) {
        final CreditCardPlasticEntity plastic = plasticRepository.findByCode(plasticCode).orElseThrow(() ->
                new EntityNotFoundException("CreditCardPlasticEntity not found for code " + plasticCode));
        if (!plastic.getCard().getCode().equals(cardCode)) throw conflict("O plástico não pertence a este cartão");
        return plastic;
    }

    private ThirdPartyEntity activeThirdParty(final UUID code) {
        final ThirdPartyEntity thirdParty = thirdPartyRepository.findByCode(code).orElseThrow(() ->
                invalid("defaultThirdPartyCode", "terceiro não encontrado"));
        if (thirdParty.getStatus() != ThirdPartyStatus.ACTIVE) throw invalid("defaultThirdPartyCode", "terceiro inativo");
        return thirdParty;
    }

    private String name(final String value) {
        final String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 1 || normalized.length() > 80) throw invalid("name", "deve ter entre 1 e 80 caracteres");
        return normalized;
    }

    private String digits(final String value) {
        if (value == null || !value.matches("\\d{4}")) throw invalid("lastDigits", "deve conter exatamente 4 dígitos");
        return value;
    }

    private static ConflictException conflict(final String message) { return new ConflictException(message); }
    private static InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException("Campos inválidos", CreditCardHolderService.class, Map.of(field, message));
    }
}
