package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.thirdparty.ThirdPartyResponse;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.service.thirdparty.ThirdParty;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ThirdPartyMapper {
    public ThirdParty toModel(final ThirdPartyEntity entity) {
        return new ThirdParty(entity.getCode(), entity.getName(), entity.getStatus());
    }
    public ThirdPartyResponse toResponse(final ThirdParty model) {
        return new ThirdPartyResponse(model.code(), model.name(), model.status(), model.openAmount(),
                model.lateAmount(), model.openCount());
    }
    public List<ThirdPartyResponse> toResponseList(final List<ThirdParty> models) {
        return models.stream().map(this::toResponse).toList();
    }
}
