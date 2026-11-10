package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.ThirdPartyMapper;
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
public class ThirdPartyService {
    private final ThirdPartyRepository repository;
    private final ThirdPartyMapper mapper;

    @Transactional
    public ThirdParty create(final String name) {
        log.info("m=create, name={}", name);
        final String normalized = name(name);
        if (repository.existsByName(normalized)) throw new ConflictException("Já existe terceiro com esse nome");
        return describe(repository.save(ThirdPartyEntity.builder().name(normalized).status(ThirdPartyStatus.ACTIVE).build()));
    }

    @Transactional(readOnly = true)
    public List<ThirdParty> find(final List<ThirdPartyStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        final List<ThirdPartyStatus> effective = statuses == null || statuses.isEmpty()
                ? List.of(ThirdPartyStatus.ACTIVE) : statuses;
        return repository.findByStatuses(effective).stream().map(this::describe).toList();
    }

    @Transactional(readOnly = true)
    public ThirdParty findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return describe(entity(code));
    }

    @Transactional
    public ThirdParty update(final UUID code, final String name) {
        log.info("m=update, code={}, name={}", code, name);
        final ThirdPartyEntity entity = entity(code);
        if (name != null) {
            final String normalized = name(name);
            if (!normalized.equals(entity.getName()) && repository.existsByNameAndCodeNot(normalized, code)) {
                throw new ConflictException("Já existe terceiro com esse nome");
            }
            entity.setName(normalized);
        }
        return describe(repository.save(entity));
    }

    @Transactional
    public ThirdParty changeStatus(final UUID code, final ThirdPartyStatus status) {
        log.info("m=changeStatus, code={}, status={}", code, status);
        final ThirdPartyEntity entity = entity(code);
        entity.setStatus(status);
        return describe(repository.save(entity));
    }

    private ThirdParty describe(final ThirdPartyEntity entity) {
        return mapper.toModel(entity);
    }

    private ThirdPartyEntity entity(final UUID code) {
        return repository.findByCode(code).orElseThrow(() -> new EntityNotFoundException(
                "ThirdPartyEntity not found for code " + code));
    }

    private String name(final String value) {
        final String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new InvalidFieldsException("Campos inválidos", ThirdPartyService.class,
                    Map.of("name", "deve ter entre 1 e 80 caracteres"));
        }
        return normalized;
    }
}
