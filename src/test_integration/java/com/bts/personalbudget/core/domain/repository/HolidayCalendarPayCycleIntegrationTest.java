package com.bts.personalbudget.core.domain.repository;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildNewSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarReviewedEvent;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(PayCycleRepositoryTest.FixedClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class HolidayCalendarPayCycleIntegrationTest {

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private HolidayCalendarService calendarService;
    @SpyBean
    private PayCycleService cycleService;
    @Autowired
    private PayCycleRepository cycleRepository;
    @Autowired
    private PayCycleConfigRepository configRepository;
    @Autowired
    private IncomeScheduleRepository scheduleRepository;
    @Autowired
    private IncomeOccurrenceRepository occurrenceRepository;
    @Autowired
    private IncomeScheduleMapper incomeScheduleMapper;
    @Autowired
    private PayCycleMapper cycleMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM pay_cycle_adjustment");
        cycleRepository.deleteAll();
        jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'CALENDAR_MONTH', anchor_schedule_id = NULL, "
                + "activation_date = NULL, version = 0");
        occurrenceRepository.deleteAll();
        scheduleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM holiday");
        jdbcTemplate.update("DELETE FROM holiday_calendar_review");
        clearInvocations(cycleService);
    }

    @Test
    void shouldPreviewIncomeBoundariesAndDivergencesWithoutAnyWrites() throws Exception {
        final List<PayCycleEntity> chain = givenChain();
        final Map<String, List<Map<String, Object>>> before = snapshot();

        mockMvc.perform(post("/holiday_calendar/2026/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"holidays":[{"date":"2026-11-05","name":"November holiday"},
                                {"date":"2026-12-07","name":"December holiday"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income_occurrences.length()").value(2))
                .andExpect(jsonPath("$.income_occurrences[0].nominal_month").value("2026-11"))
                .andExpect(jsonPath("$.income_occurrences[0].before.planned_date").value("2026-11-05"))
                .andExpect(jsonPath("$.income_occurrences[0].after.planned_date").value("2026-11-06"))
                .andExpect(jsonPath("$.cycle_boundaries.length()").value(1))
                .andExpect(jsonPath("$.cycle_boundaries[0].previous_cycle_code").value(chain.get(1).getCode().toString()))
                .andExpect(jsonPath("$.cycle_boundaries[0].next_cycle_code").value(chain.get(2).getCode().toString()))
                .andExpect(jsonPath("$.cycle_boundaries[0].old_boundary").value("2026-12-07"))
                .andExpect(jsonPath("$.cycle_boundaries[0].new_boundary").value("2026-12-08"))
                .andExpect(jsonPath("$.cycle_divergences.length()").value(1))
                .andExpect(jsonPath("$.cycle_divergences[0].type").value("BOUNDARY_DIVERGES_FROM_ANCHOR"))
                .andExpect(jsonPath("$.cycle_divergences[0].boundary_date").value("2026-11-05"))
                .andExpect(jsonPath("$.cycle_divergences[0].anchor_date").value("2026-11-06"));

        assertThat(snapshot()).usingRecursiveComparison().isEqualTo(before);
        verify(cycleService, never()).refresh();
    }

    @Test
    void shouldRefreshOnlyAfterCommitUsingIndependentTransactionAndKeepChainContiguous() {
        final List<PayCycleEntity> chain = givenChain();
        final AtomicReference<Object> reviewResource = new AtomicReference<>();
        final AtomicBoolean refreshed = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionAspectSupport.currentTransactionStatus().isNewTransaction()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory))
                    .isNotSameAs(reviewResource.get());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM holiday WHERE holiday_date = '2026-12-07'", Integer.class)).isEqualTo(1);
            invocation.callRealMethod();
            refreshed.set(true);
            return null;
        }).when(cycleService).refresh();

        transactionTemplate.executeWithoutResult(transaction -> {
            reviewResource.set(TransactionSynchronizationManager.getResource(entityManagerFactory));
            calendarService.reviewYear(2026, List.of(new Holiday(DEC_7, "Holiday")));
            verify(cycleService, never()).refresh();
            assertThat(refreshed).isFalse();
        });

        assertThat(refreshed).isTrue();
        verify(cycleService).refresh();
        final List<PayCycleEntity> active = cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        assertThat(active).hasSize(3);
        assertThat(active.get(0).getEndDate()).isEqualTo(NOV_5);
        assertThat(active.get(1).getEndDate()).isEqualTo(DEC_7.plusDays(1));
        assertThat(active.get(2).getStartDate()).isEqualTo(DEC_7.plusDays(1));
        assertThat(active).extracting(PayCycleEntity::getCode)
                .containsExactlyElementsOf(chain.stream().map(PayCycleEntity::getCode).toList());
        for (int i = 1; i < active.size(); i++) {
            assertThat(active.get(i).getStartDate()).isEqualTo(active.get(i - 1).getEndDate());
        }
        assertThat(active).allSatisfy(cycle -> assertThat(cycle.getStartDate()).isBefore(cycle.getEndDate()));
    }

    @Test
    void shouldNotRefreshWhenReviewTransactionRollsBack() {
        givenChain();
        final Map<String, List<Map<String, Object>>> before = snapshot();

        transactionTemplate.executeWithoutResult(transaction -> {
            calendarService.reviewYear(2026, List.of(new Holiday(DEC_7, "Holiday")));
            transaction.setRollbackOnly();
        });

        verify(cycleService, never()).refresh();
        assertThat(snapshot()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void shouldNotRefreshEventPublishedOutsideTransaction() {
        eventPublisher.publishEvent(new HolidayCalendarReviewedEvent(2026));

        verify(cycleService, never()).refresh();
    }

    @Test
    void shouldRollBackFailedRefreshButKeepCalendarReviewCommittedAndLogFailure(final CapturedOutput output) {
        givenChain();
        final List<Map<String, Object>> before = jdbcTemplate.queryForList("SELECT * FROM pay_cycle ORDER BY id");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Forced refresh failure after writes");
        }).when(cycleService).refresh();

        assertDoesNotThrow(() -> calendarService.reviewYear(2026, List.of(new Holiday(DEC_7, "Holiday"))));

        assertThat(calendarService.findYear(2026).reviewed()).isTrue();
        assertThat(calendarService.findYear(2026).holidays()).containsExactly(new Holiday(DEC_7, "Holiday"));
        assertThat(jdbcTemplate.queryForList("SELECT * FROM pay_cycle ORDER BY id"))
                .usingRecursiveComparison().isEqualTo(before);
        assertThat(output.getOut()).contains("message=PayCycleRefreshFailed", "year=2026",
                "Forced refresh failure after writes");
        verify(cycleService).refresh();
    }

    private List<PayCycleEntity> givenChain() {
        jdbcTemplate.update("INSERT INTO holiday_calendar_review (calendar_year) VALUES (2026), (2027)");
        final IncomeScheduleEntity anchor = incomeScheduleMapper.toEntity(
                buildNewSchedule(YearMonth.of(2026, 10), true, null));
        anchor.setDescription("Anchor income");
        anchor.setStatus(com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus.ACTIVE);
        scheduleRepository.saveAndFlush(anchor);
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        config.setMode(PayCycleMode.PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(OCT_5);
        configRepository.saveAndFlush(config);
        return cycleRepository.saveAllAndFlush(List.of(
                cycleMapper.toEntity(buildCycle(OCT_5, NOV_5, YearMonth.of(2026, 10)), anchor),
                cycleMapper.toEntity(buildCycle(NOV_5, DEC_7, YearMonth.of(2026, 11)), anchor),
                cycleMapper.toEntity(buildCycle(DEC_7, JAN_5, YearMonth.of(2026, 12)), anchor)));
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        return Map.of(
                "calendar", jdbcTemplate.queryForList("SELECT * FROM holiday ORDER BY id"),
                "reviews", jdbcTemplate.queryForList("SELECT * FROM holiday_calendar_review ORDER BY calendar_year"),
                "cycles", jdbcTemplate.queryForList("SELECT * FROM pay_cycle ORDER BY id"),
                "config", jdbcTemplate.queryForList("SELECT * FROM pay_cycle_config ORDER BY id"),
                "adjustments", jdbcTemplate.queryForList("SELECT * FROM pay_cycle_adjustment ORDER BY id"),
                "occurrences", jdbcTemplate.queryForList("SELECT * FROM income_occurrence ORDER BY id"),
                "movements", jdbcTemplate.queryForList("SELECT * FROM financial_movement ORDER BY id"),
                "schedules", jdbcTemplate.queryForList("SELECT * FROM income_schedule ORDER BY id"));
    }
}
