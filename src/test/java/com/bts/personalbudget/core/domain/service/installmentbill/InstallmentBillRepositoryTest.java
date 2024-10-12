package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.repository.InstallmentBillJpaRepository;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InstallmentBillRepositoryTest {

    @InjectMocks
    private InstallmentBillRepository repository;

    @Mock
    private InstallmentBillJpaRepository jpaRepository;

    @Spy
    private InstallmentBillMapper mapper = InstallmentBillMapper.INSTANCE;

    @Test
    void shouldSaveInstallmentBillEntity() {
        InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.DEBIT, "Test", BigDecimal.TEN, LocalDate.now(), 1);
        repository.save(installmentBill);
        ArgumentCaptor<InstallmentBillEntity> argumentCaptor = ArgumentCaptor.forClass(InstallmentBillEntity.class);
        verify(jpaRepository).save(argumentCaptor.capture());
        InstallmentBillEntity installmentBillEntity = argumentCaptor.getValue();
        testEquals(installmentBill, installmentBillEntity);
    }

    @Test
    void shouldFindAllInstallmentBill() {
        InstallmentBillEntity installmentBillEntity = InstallmentBillFactory.buildEntity();
        when(jpaRepository.findAllByFlagActive(Boolean.TRUE)).thenReturn(List.of(installmentBillEntity));
        List<InstallmentBill> installmentBillList = repository.findAll();
        assertFalse(installmentBillList.isEmpty());
        testEquals(installmentBillList.stream().findFirst().get(), installmentBillEntity);
    }

    @Test
    void shouldFindInstallmentBillByCode() {
        InstallmentBillEntity installmentBillEntity = InstallmentBillFactory.buildEntity();
        UUID code = installmentBillEntity.getCode();
        when(jpaRepository.findByCode(code)).thenReturn(Optional.of(installmentBillEntity));
        InstallmentBill installmentBill = repository.findByCode(code);
        assertNotNull(installmentBill);
        testEquals(installmentBill, installmentBillEntity);
    }

    @Test
    void shouldUpdateInstallmentBill() {
        InstallmentBill installmentBill = InstallmentBillFactory.buildModel("Update Test");
        when(jpaRepository.findByCode(installmentBill.getCode())).
                thenReturn(Optional.ofNullable(InstallmentBillFactory.buildEntity()));
        InstallmentBill updatedInstallmentBill = repository.update(installmentBill);
        assertEquals(installmentBill, updatedInstallmentBill);
    }

    @Test
    void shouldDeleteInstallmentBill() {
        UUID code = UUID.randomUUID();
        InstallmentBillEntity installmentBillEntity = InstallmentBillFactory.buildEntity();
        when(jpaRepository.findByCode(code)).thenReturn(Optional.ofNullable(installmentBillEntity));
        repository.delete(code);
        ArgumentCaptor<InstallmentBillEntity> argumentCaptor = ArgumentCaptor.forClass(InstallmentBillEntity.class);
        verify(jpaRepository).save(argumentCaptor.capture());
        InstallmentBillEntity deletedInstallmentBillEntity = argumentCaptor.getValue();
        assertFalse(deletedInstallmentBillEntity.getFlagActive());
    }

    @Test
    void shouldThrowInstallmentBillAlreadyDeletedExceptionIfAlreadyDeletedInstallmentBill() {
        InstallmentBillEntity installmentBillEntity = InstallmentBillFactory.entityBuilder().flagActive(Boolean.FALSE).build();
        UUID code = installmentBillEntity.getCode();
        when(jpaRepository.findByCode(code)).thenReturn(Optional.of(installmentBillEntity));
        assertThrows(InstallmentBillAlreadyDeletedException.class, () -> repository.delete(code));
    }

    @Test
    public void shouldThrowsExceptionWhenNotFoundInstallmentBillByCode() {
        when(jpaRepository.findByCode(any())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> repository.findByCode(UUID.randomUUID()));
    }

    private void testEquals(InstallmentBill installmentBill, InstallmentBillEntity entity) {
        assertEquals(installmentBill.getCode(), entity.getCode());
        assertEquals(installmentBill.getDescription(), entity.getDescription());
        assertEquals(installmentBill.getAmount(), entity.getAmount());
        assertEquals(installmentBill.getPurchaseDate(), entity.getPurchaseDate());
        assertEquals(installmentBill.getInstallmentTotal(), entity.getInstallmentTotal());
        assertEquals(installmentBill.getLastInstallmentDate(), entity.getLastInstallmentDate());
        assertEquals(installmentBill.getNextInstallmentDate(), entity.getNextInstallmentDate());
    }

}
