package com.bts.personalbudget.mapper;

import com.bts.personalbudget.core.domain.entity.ReceivableWriteOffEntity;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableWriteOff;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReceivableWriteOffMapper {
    public ReceivableWriteOff toModel(final ReceivableWriteOffEntity entity) {
        return new ReceivableWriteOff(entity.getCode(), entity.getCategory().getCode(), entity.getAmount(),
                entity.getEffectiveDate());
    }

    public List<ReceivableWriteOff> toModelList(final List<ReceivableWriteOffEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }
}
