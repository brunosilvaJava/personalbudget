package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.mapper.ReceivableWriteOffMapper;
import com.bts.personalbudget.repository.ReceivableWriteOffRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the write-offs, which the reports of competence count as the user's own expense. */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReceivableWriteOffService {
    private final ReceivableWriteOffRepository repository;
    private final ReceivableWriteOffMapper mapper;

    @Transactional(readOnly = true)
    public List<ReceivableWriteOff> findByEffectiveInterval(final LocalDate from, final LocalDate toExclusive) {
        log.info("m=findByEffectiveInterval, from={}, toExclusive={}", from, toExclusive);
        return mapper.toModelList(repository.findActiveByEffectiveDateInterval(from, toExclusive));
    }
}
