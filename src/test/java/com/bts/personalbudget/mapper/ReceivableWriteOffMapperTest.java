package com.bts.personalbudget.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.ReceivableWriteOffEntity;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableWriteOff;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableWriteOffMapperTest {
    private final ReceivableWriteOffMapper mapper = new ReceivableWriteOffMapper();

    @Test
    void shouldMapTheCategoryCodeTheAmountAndTheDate() {
        final UUID code = UUID.randomUUID();
        final UUID categoryCode = UUID.randomUUID();
        final ReceivableWriteOffEntity entity = ReceivableWriteOffEntity.builder().code(code)
                .category(CategoryEntity.builder().code(categoryCode).build())
                .amount(new BigDecimal("70.00")).effectiveDate(LocalDate.of(2026, 11, 10)).build();

        final ReceivableWriteOff model = mapper.toModel(entity);

        assertThat(model.code()).isEqualTo(code);
        assertThat(model.categoryCode()).isEqualTo(categoryCode);
        assertThat(model.amount()).isEqualByComparingTo("70.00");
        assertThat(model.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }
}
