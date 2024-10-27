package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.CalcFixedBill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class CalcFixedBillFactory {

    @Autowired
    private ApplicationContext applicationContext;

    public CalcFixedBill build(final RecurrenceType recurrenceType) {
        return applicationContext.getBean(
                CalcFixedBill.class.getSimpleName() + "." + recurrenceType.name(),
                CalcFixedBill.class);
    }

}
