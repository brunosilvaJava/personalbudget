package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FixedBillServiceFactory {

    @Autowired
    private ApplicationContext applicationContext;

    public FixedBillService build(final RecurrenceType recurrenceType) {
        return applicationContext.getBean(
                FixedBillService.class.getSimpleName() + "." + recurrenceType.name(),
                FixedBillService.class);
    }

}
