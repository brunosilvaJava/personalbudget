package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bts.personalbudget.core.domain.factory.CategoryFactory.CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.OTHER_CATEGORY_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InstallmentBillServiceTest {

    @InjectMocks
    private InstallmentBillService service;

    @Mock
    private InstallmentBillRepository repository;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private CategoryService categoryService;

    @Spy
    private FinancialMovementMapper financialMovementMapper = FinancialMovementMapper.INSTANCE;

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(FIXED_INSTANT, ZONE_ID);

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZONE_ID);

    @BeforeEach
    void returnUpdatedInstallmentBillFromRepository() {
        lenient().when(repository.update(any(InstallmentBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateInstallmentBill() {
        InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.CREDIT, "Test", BigDecimal.TEN, TODAY, 1);
        service.create(installmentBill);
        verify(repository).save(installmentBill);
        // First installment is purchase_date + 30 days: nothing is due yet.
        verify(repository, never()).findForUpdateByCode(any());
        verify(financialMovementService, never()).create(any(FinancialMovement.class));
    }

    @Test
    void shouldPostOverdueInstallmentsWhenPurchaseDateIsInThePast() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.DEBIT, "Past purchase", BigDecimal.TEN, TODAY.minusDays(45), 3);
        final UUID code = installmentBill.getCode();
        final LocalDate firstInstallment = TODAY.minusDays(15);
        assertEquals(firstInstallment, installmentBill.getNextInstallmentDate());

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(code, firstInstallment)).thenReturn(false);
        when(repository.findByCode(code)).thenReturn(installmentBill);

        final InstallmentBill created = service.create(installmentBill);

        verify(repository).save(installmentBill);
        final ArgumentCaptor<FinancialMovement> captor = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService).create(captor.capture());
        assertEquals(firstInstallment, captor.getValue().occurrenceDate());

        assertEquals(1, installmentBill.getInstallmentCount());
        assertEquals(firstInstallment.plusDays(30), installmentBill.getNextInstallmentDate());
        assertEquals(InstallmentBillStatus.PENDING, installmentBill.getStatus());
        assertEquals(1, created.getInstallmentCount());
        assertEquals(firstInstallment.plusDays(30), created.getNextInstallmentDate());
    }

//    @Test
//    void shouldFindAllInstallmentBill() {
//        InstallmentBill installmentBill = InstallmentBillFactory.buildModel();
//        when(repository.findAll()).thenReturn(List.of(installmentBill));
//        List<InstallmentBill> installmentBillList = service.findAll();
//        assertFalse(installmentBillList.isEmpty());
//        assertEquals(installmentBill, installmentBillList.stream().findFirst().get());
//    }

    @Test
    void shouldFindInstallmentBillByCode() {
        InstallmentBill installmentBillMock = InstallmentBillFactory.buildModel();
        UUID code = installmentBillMock.getCode();
        when(repository.findByCode(code)).thenReturn(installmentBillMock);
        InstallmentBill installmentBill = service.findByCode(code);
        assertNotNull(installmentBill);
        assertEquals(installmentBillMock, installmentBill);
    }

    @Test
    void shouldUpdateInstallmentBill() {

        InstallmentBill installmentBill = InstallmentBillFactory.buildModel();

        UUID code = installmentBill.getCode();
        OperationType operationType = OperationType.CREDIT;
        String description = "Test Update";
        BigDecimal amount = BigDecimal.valueOf(100L);
        LocalDate purchaseDate = LocalDate.now().minusDays(1);
        Integer installmentTotal = 10;

        when(repository.findByCode(code)).thenReturn(installmentBill);

        service.update(code, operationType, description, amount, purchaseDate, installmentTotal, null);

        ArgumentCaptor<InstallmentBill> argumentCaptor = ArgumentCaptor.forClass(InstallmentBill.class);

        verify(repository).update(argumentCaptor.capture());

        InstallmentBill savedInstallmentBill = argumentCaptor.getValue();

        assertEquals(operationType, savedInstallmentBill.getOperationType());
        assertEquals(description, savedInstallmentBill.getDescription());
        assertEquals(amount, savedInstallmentBill.getAmount());
        assertEquals(purchaseDate, savedInstallmentBill.getPurchaseDate());
        assertEquals(installmentTotal, savedInstallmentBill.getInstallmentTotal());
    }

    @Test
    void shouldNotRescheduleNextInstallmentDateFromPurchaseDateAfterInstallmentsWerePosted() {
        final UUID code = UUID.randomUUID();
        final InstallmentBill installmentBill = new InstallmentBill(
                code, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                TODAY.minusDays(120), 6, 2, TODAY.minusDays(5), TODAY.plusDays(25));
        when(repository.findByCode(code)).thenReturn(installmentBill);

        final LocalDate newPurchaseDate = TODAY.minusDays(100);
        service.update(code, null, null, null, newPurchaseDate, null, null);

        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(TODAY.plusDays(25), captor.getValue().getNextInstallmentDate());
        assertEquals(newPurchaseDate, captor.getValue().getPurchaseDate());
    }

    @Test
    void shouldRecalculateNextInstallmentDateFromPurchaseDateBeforeFirstInstallment() {
        final UUID code = UUID.randomUUID();
        final InstallmentBill installmentBill = new InstallmentBill(
                code, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                TODAY.minusDays(120), 6, 0, null, TODAY.minusDays(90));
        when(repository.findByCode(code)).thenReturn(installmentBill);

        final LocalDate newPurchaseDate = TODAY.minusDays(20);
        service.update(code, null, null, null, newPurchaseDate, null, null);

        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(newPurchaseDate.plusDays(30), captor.getValue().getNextInstallmentDate());
    }

    @Test
    void shouldRejectInstallmentTotalBelowAlreadyPostedInstallments() {
        final UUID code = UUID.randomUUID();
        final InstallmentBill installmentBill = new InstallmentBill(
                code, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                TODAY.minusDays(120), 6, 2, TODAY.minusDays(60), TODAY.minusDays(30));
        when(repository.findByCode(code)).thenReturn(installmentBill);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.update(code, null, null, null, null, 1, null));

        assertEquals(Map.of("installmentTotal", "não pode ser menor que o número de parcelas já lançadas"),
                exception.getValidations());
        verify(repository, never()).update(any());
    }

    @Test
    void shouldConcludeInstallmentBillWhenUpdatedTotalEqualsInstallmentCount() {
        final UUID code = UUID.randomUUID();
        final InstallmentBill installmentBill = new InstallmentBill(
                code, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                TODAY.minusDays(120), 6, 2, TODAY.minusDays(60), TODAY.minusDays(30));
        when(repository.findByCode(code)).thenReturn(installmentBill);

        final InstallmentBill updated = service.update(code, null, null, null, null, 2, null);

        assertEquals(InstallmentBillStatus.DONE, updated.getStatus());
        assertNull(updated.getNextInstallmentDate());
    }

    @Test
    void shouldPostInstallmentsMadeDueByPurchaseDateUpdate() {
        final UUID code = UUID.randomUUID();
        final InstallmentBill installmentBill = new InstallmentBill(
                code, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                TODAY.minusDays(45), 3, 0, null, TODAY.minusDays(15));
        when(repository.findByCode(code)).thenReturn(installmentBill);
        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(eq(code), any()))
                .thenReturn(false);

        service.update(code, null, null, null, TODAY.minusDays(70), null, null);

        verify(financialMovementService, times(2)).create(any(FinancialMovement.class));
        assertEquals(2, installmentBill.getInstallmentCount());
        assertEquals(TODAY.plusDays(20), installmentBill.getNextInstallmentDate());
    }

    @Test
    void shouldDeleteInstallmentBill() {
        UUID code = UUID.randomUUID();
        service.delete(code);
        verify(repository).delete(code);
    }

    @Test
    void shouldDeleteInstallmentBillIdempotentlyWhenAlreadyDeleted() {
        UUID code = UUID.randomUUID();
        doThrow(new InstallmentBillAlreadyDeletedException(code)).when(repository).delete(code);
        service.delete(code);
    }

    @Test
    public void shouldThrowsExceptionWhenNotFoundInstallmentBillByCode() {
        UUID code = UUID.randomUUID();
        doThrow(new RuntimeException()).when(repository).delete(code);
        assertThrows(RuntimeException.class, () -> service.delete(code));
    }

    @Test
    void shouldPostOverdueInstallmentsWhenJobSkippedDays() {
        final UUID code = UUID.randomUUID();
        final LocalDate today = LocalDate.now();
        final LocalDate firstOverdueInstallment = today.minusDays(65);
        final InstallmentBill installmentBill = new InstallmentBill(code, OperationType.DEBIT, "Catch-up test",
                BigDecimal.TEN, InstallmentBillStatus.PENDING, today.minusDays(95), 5,
                0, null, firstOverdueInstallment);

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(eq(code), any())).thenReturn(false);

        service.postDueInstallments(code, today);

        final ArgumentCaptor<FinancialMovement> captor = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService, times(3)).create(captor.capture());

        final List<LocalDate> postedOccurrences = captor.getAllValues().stream()
                .map(FinancialMovement::occurrenceDate)
                .toList();
        assertEquals(List.of(firstOverdueInstallment, firstOverdueInstallment.plusDays(30), firstOverdueInstallment.plusDays(60)),
                postedOccurrences);
        assertEquals(3, installmentBill.getInstallmentCount());
        assertEquals(InstallmentBillStatus.PENDING, installmentBill.getStatus());
        assertEquals(firstOverdueInstallment.plusDays(90), installmentBill.getNextInstallmentDate());
    }

    @Test
    void shouldConcludeInstallmentBillWhenLastInstallmentIsCaughtUp() {
        final UUID code = UUID.randomUUID();
        final LocalDate today = LocalDate.now();
        final LocalDate lastOverdueInstallment = today.minusDays(10);
        final InstallmentBill installmentBill = new InstallmentBill(code, OperationType.DEBIT, "Catch-up test",
                BigDecimal.TEN, InstallmentBillStatus.PENDING, today.minusDays(40), 1,
                0, null, lastOverdueInstallment);

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(code, lastOverdueInstallment)).thenReturn(false);

        service.postDueInstallments(code, today);

        verify(financialMovementService, times(1)).create(any(FinancialMovement.class));

        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(InstallmentBillStatus.DONE, captor.getValue().getStatus());

        assertEquals(InstallmentBillStatus.DONE, installmentBill.getStatus());
        assertNull(installmentBill.getNextInstallmentDate());
        assertEquals(1, installmentBill.getInstallmentCount());
    }

    @Test
    void shouldCreateInstallmentBillWithCategory() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.DEBIT, "Test", BigDecimal.TEN, TODAY, 1);
        installmentBill.setCategoryCode(CATEGORY_CODE);

        service.create(installmentBill);

        verify(categoryService).validateForUse(CATEGORY_CODE, OperationType.DEBIT);
        verify(repository).save(installmentBill);
    }

    @Test
    void shouldRejectInstallmentBillWhenCategoryIsInactive() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.DEBIT, "Test", BigDecimal.TEN, TODAY, 1);
        installmentBill.setCategoryCode(CATEGORY_CODE);
        doThrow(categoryRejection()).when(categoryService).validateForUse(CATEGORY_CODE, OperationType.DEBIT);

        assertThrows(InvalidFieldsException.class, () -> service.create(installmentBill));

        verify(repository, never()).save(any());
    }

    @Test
    void shouldInheritCategoryWhenInstallmentBillPostsInstallment() {
        final UUID code = UUID.randomUUID();
        final LocalDate today = LocalDate.now();
        final LocalDate overdueInstallment = today.minusDays(10);
        final InstallmentBill installmentBill = new InstallmentBill(code, OperationType.DEBIT, "Catch-up test",
                BigDecimal.TEN, InstallmentBillStatus.PENDING, today.minusDays(40), 2,
                0, null, overdueInstallment);
        installmentBill.setCategoryCode(CATEGORY_CODE);

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(code, overdueInstallment)).thenReturn(false);

        service.postDueInstallments(code, today);

        final ArgumentCaptor<FinancialMovement> captor = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService).create(captor.capture());
        assertEquals(CATEGORY_CODE, captor.getValue().categoryCode());
        // The job never asks whether the category is still usable.
        verifyNoInteractions(categoryService);
    }

    @Test
    void shouldReplaceCategoryWhenInstallmentBillUpdateHasCategory() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel();
        installmentBill.setCategoryCode(CATEGORY_CODE);
        final UUID code = installmentBill.getCode();
        when(repository.findByCode(code)).thenReturn(installmentBill);

        service.update(code, null, null, null, null, null, OTHER_CATEGORY_CODE);

        verify(categoryService).validateForUse(OTHER_CATEGORY_CODE, OperationType.DEBIT);
        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(OTHER_CATEGORY_CODE, captor.getValue().getCategoryCode());
    }

    @Test
    void shouldKeepCategoryWhenInstallmentBillUpdateHasNoCategory() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel();
        installmentBill.setCategoryCode(CATEGORY_CODE);
        final UUID code = installmentBill.getCode();
        when(repository.findByCode(code)).thenReturn(installmentBill);

        service.update(code, null, "Nova descrição", null, null, null, null);

        verifyNoInteractions(categoryService);
        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(CATEGORY_CODE, captor.getValue().getCategoryCode());
    }

    @Test
    void shouldRejectInstallmentBillUpdateWhenOperationTypeChangesAndCategoryNoLongerMatches() {
        final InstallmentBill installmentBill = InstallmentBillFactory.buildModel();
        installmentBill.setCategoryCode(CATEGORY_CODE);
        final UUID code = installmentBill.getCode();
        when(repository.findByCode(code)).thenReturn(installmentBill);
        doThrow(categoryRejection()).when(categoryService).validateForUse(CATEGORY_CODE, OperationType.CREDIT);

        assertThrows(InvalidFieldsException.class,
                () -> service.update(code, OperationType.CREDIT, null, null, null, null, null));

        verify(repository, never()).update(any());
    }

    private static InvalidFieldsException categoryRejection() {
        return new InvalidFieldsException("Campos inválidos", CategoryService.class,
                Map.of("categoryCode", "categoria inativa"));
    }

}
