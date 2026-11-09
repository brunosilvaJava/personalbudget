package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelope;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelopeService;
import com.bts.personalbudget.core.domain.service.budgetenvelope.CycleEnvelopes;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A cycle in 2090 with one expense category: 100.00 already paid and 200.00 still to pay. */
@Testcontainers
@SpringBootTest
class BudgetEnvelopeIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    private static final UUID CYCLE_CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e1");
    private static final UUID PAID_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");
    private static final UUID PENDING_CODE = UUID.fromString("cccccccc-0000-0000-0000-0000000000e1");
    private static final LocalDate COMPETENCE = LocalDate.of(2090, 11, 10);

    @Autowired
    private BudgetEnvelopeService service;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private MovementSettlementService settlementService;

    @Autowired
    private PayCycleRepository cycleRepository;

    @Autowired
    private FinancialMovementRepository movementRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID category;

    @BeforeEach
    void setUp() {
        cleanData();
        category = categoryService.find(OperationType.DEBIT, null, null).get(0).code();
        cycleRepository.saveAndFlush(PayCycleEntity.builder().code(CYCLE_CODE)
                .startDate(LocalDate.of(2090, 11, 1)).endDate(LocalDate.of(2091, 1, 1))
                .kind(PayCycleKind.TRANSITION).lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false).endAdjusted(false).version(0L).build());
        movementRepository.saveAndFlush(expense(PAID_CODE, "-100.00"));
        movementRepository.saveAndFlush(expense(PENDING_CODE, "-200.00"));
        settlementService.create(PAID_CODE, new MovementSettlement(null, null, new BigDecimal("100.00"),
                LocalDate.of(2090, 11, 12), null, null, null, null, null, null));
    }

    @AfterEach
    void cleanData() {
        jdbc.update("DELETE FROM budget_envelope WHERE pay_cycle_id IN "
                + "(SELECT id FROM pay_cycle WHERE code = UUID_TO_BIN(?))", CYCLE_CODE.toString());
        jdbc.update("DELETE FROM movement_settlement WHERE financial_movement_id IN (SELECT id FROM "
                        + "financial_movement WHERE code IN (UUID_TO_BIN(?), UUID_TO_BIN(?)))",
                PAID_CODE.toString(), PENDING_CODE.toString());
        jdbc.update("DELETE FROM financial_movement WHERE code IN (UUID_TO_BIN(?), UUID_TO_BIN(?))",
                PAID_CODE.toString(), PENDING_CODE.toString());
        jdbc.update("DELETE FROM pay_cycle WHERE code = UUID_TO_BIN(?)", CYCLE_CODE.toString());
    }

    private FinancialMovementEntity expense(final UUID code, final String amount) {
        final LocalDateTime due = COMPETENCE.atStartOfDay();
        return FinancialMovementEntity.builder().code(code).flagActive(true).operationType(OperationType.DEBIT)
                .description("Envelope test").amount(new BigDecimal(amount)).amountPaid(BigDecimal.ZERO)
                .movementDate(due).dueDate(due).status(FinancialMovementStatus.PENDING)
                .categoryCode(category).competenceDate(COMPETENCE).competenceOverridden(true).build();
    }

    @Test
    void shouldSplitConsumptionIntoPaidAndStillToPayAndReserveWhatIsLeft() {
        final BudgetEnvelope created = service.create(CYCLE_CODE, category, new BigDecimal("500.00"));

        assertThat(created.recognizedAmount()).isEqualByComparingTo("100.00");
        assertThat(created.committedAmount()).isEqualByComparingTo("200.00");
        assertThat(created.consumedAmount()).isEqualByComparingTo("300.00");
        assertThat(created.reservedAmount()).isEqualByComparingTo("200.00");

        final CycleEnvelopes listed = service.find(CYCLE_CODE);
        assertThat(listed.envelopes()).hasSize(1);
        assertThat(listed.totalReserved()).isEqualByComparingTo("200.00");

        final BudgetEnvelope shrunk = service.update(created.code(), new BigDecimal("250.00"));
        assertThat(shrunk.reservedAmount()).isEqualByComparingTo("0");
        assertThat(shrunk.overrunAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldAllowOneActiveEnvelopePerCategoryAndRecreateAfterDeleting() {
        final BudgetEnvelope created = service.create(CYCLE_CODE, category, new BigDecimal("500.00"));

        assertThrows(ConflictException.class, () -> service.create(CYCLE_CODE, category, new BigDecimal("10.00")));
        service.delete(created.code());
        service.delete(created.code());

        assertThat(service.find(CYCLE_CODE).envelopes()).isEmpty();
        assertThat(service.create(CYCLE_CODE, category, new BigDecimal("300.00")).code())
                .isNotEqualTo(created.code());
    }

    @Test
    void shouldNotChangeEnvelopesOfAClosedCycle() {
        final BudgetEnvelope created = service.create(CYCLE_CODE, category, new BigDecimal("500.00"));
        jdbc.update("UPDATE pay_cycle SET lifecycle = 'CLOSED' WHERE code = UUID_TO_BIN(?)", CYCLE_CODE.toString());

        assertThrows(ConflictException.class, () -> service.update(created.code(), new BigDecimal("1.00")));
        assertThrows(ConflictException.class, () -> service.delete(created.code()));
        assertThat(service.find(CYCLE_CODE).envelopes()).hasSize(1);
    }
}
