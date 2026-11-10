package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ThirdPartyRepository extends JpaRepository<ThirdPartyEntity, Long> {
    Optional<ThirdPartyEntity> findByCode(UUID code);
    boolean existsByName(String name);
    boolean existsByNameAndCodeNot(String name, UUID code);

    @Query("SELECT t FROM ThirdPartyEntity t WHERE t.status IN :statuses ORDER BY t.name ASC")
    List<ThirdPartyEntity> findByStatuses(@Param("statuses") List<ThirdPartyStatus> statuses);
}
