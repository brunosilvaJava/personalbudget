package com.bts.personalbudget.core.domain.repository;

import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.TRANSITION;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.CLOSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSnapshot;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSnapshotService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PayCycleSnapshotIntegrationTest {
    private static final UUID CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000221");
    private static final UUID CATEGORY = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000221");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PayCycleRepository cycles;
    @Autowired private PayCycleConfigService configService;
    @Autowired private PayCycleSnapshotService snapshots;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        cleanData();
        cycles.saveAndFlush(PayCycleEntity.builder().code(CODE).startDate(START).endDate(END)
                .kind(TRANSITION).lifecycle(CLOSED).startAdjusted(false).endAdjusted(false).version(0L).build());
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    @Test
    void shouldPersistVersionedSnapshotsAndPreserveCategoryNames() {
        transactions.executeWithoutResult(status -> snapshots.recordClosure(summary(0L, "Casa")));
        transactions.executeWithoutResult(status -> {
            configService.lock();
            final PayCycleEntity cycle = cycles.findForUpdateByCode(CODE).orElseThrow();
            cycle.setLifecycle(ACTIVE);
            cycles.flush();
            cycle.setLifecycle(CLOSED);
            cycles.flush();
            snapshots.recordClosure(summary(cycle.getVersion(), "Moradia"));
        });

        final List<PayCycleSnapshot> result = snapshots.findSnapshots(CODE);
        assertThat(result).extracting(PayCycleSnapshot::snapshotVersion).containsExactly(2, 1);
        assertThat(result.get(1).categories()).extracting(PayCycleSnapshot.Category::categoryName)
                .containsExactly("Casa", "Sem categoria");
        assertThat(result.get(0).categories()).extracting(PayCycleSnapshot.Category::categoryName)
                .containsExactly("Moradia", "Sem categoria");
        assertThat(result.get(1).closedAt()).isNotNull();
        assertThat(result.get(1).recognizedExpense()).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldRollbackSnapshotAndCategoriesWithTheClosureTransaction() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            snapshots.recordClosure(summary(0L, "Casa"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(snapshots.findSnapshots(CODE)).isEmpty();
    }

    @Test
    void shouldRequireAnExistingTransaction() {
        assertThatThrownBy(() -> snapshots.recordClosure(summary(0L, "Casa")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void shouldEnforceUniqueVersionAndUncategorizedKey() {
        transactions.executeWithoutResult(status -> snapshots.recordClosure(summary(0L, "Casa")));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pay_cycle_snapshot
                    (code, pay_cycle_id, snapshot_version, cycle_version, recognized_revenue,
                     recognized_expense, projected_revenue, projected_expense, created_date)
                SELECT UUID_TO_BIN(UUID()), pay_cycle_id, snapshot_version, cycle_version + 1,
                       recognized_revenue, recognized_expense, projected_revenue, projected_expense, created_date
                FROM pay_cycle_snapshot WHERE code = UUID_TO_BIN(?)
                """, snapshots.findSnapshots(CODE).get(0).code().toString()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pay_cycle_snapshot_category
                    (snapshot_id, category_key, category_code, category_name, operation_type,
                     recognized_amount, projected_amount)
                SELECT sc.snapshot_id, sc.category_key, sc.category_code, sc.category_name, sc.operation_type,
                       sc.recognized_amount, sc.projected_amount
                FROM pay_cycle_snapshot_category sc
                JOIN pay_cycle_snapshot s ON sc.snapshot_id = s.id
                WHERE s.code = UUID_TO_BIN(?) AND sc.category_key = 'UNCATEGORIZED'
                """, snapshots.findSnapshots(CODE).get(0).code().toString()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static PayCycleSummary summary(long version, String categoryName) {
        final PayCycle cycle = new PayCycle(CODE, START, END, TRANSITION, null, null,
                CLOSED, false, false, version);
        return new PayCycleSummary(new PayCycleView(cycle, PayCycleStatus.CLOSED, List.of()),
                BigDecimal.ZERO, new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("20.00"),
                List.of(new PayCycleSummary.CategoryDetail(CATEGORY, categoryName, DEBIT,
                                new BigDecimal("10.00"), new BigDecimal("15.00")),
                        new PayCycleSummary.CategoryDetail(null, "Sem categoria", DEBIT,
                                new BigDecimal("5.00"), new BigDecimal("5.00"))), List.of());
    }

    private void cleanData() {
        jdbc.update("DELETE FROM pay_cycle_snapshot_category WHERE snapshot_id IN "
                + "(SELECT s.id FROM pay_cycle_snapshot s JOIN pay_cycle c ON s.pay_cycle_id = c.id "
                + "WHERE c.code = UUID_TO_BIN(?))", CODE.toString());
        jdbc.update("DELETE s FROM pay_cycle_snapshot s JOIN pay_cycle c ON s.pay_cycle_id = c.id "
                + "WHERE c.code = UUID_TO_BIN(?)", CODE.toString());
        jdbc.update("DELETE FROM pay_cycle WHERE code = UUID_TO_BIN(?)", CODE.toString());
    }
}
