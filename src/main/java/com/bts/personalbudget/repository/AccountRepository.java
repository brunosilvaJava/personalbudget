package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.AccountEntity;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByCode(UUID code);

    Optional<AccountEntity> findByDefaultAccountTrue();

    @Query("""
            SELECT a FROM AccountEntity a
            WHERE a.status IN :statuses
            ORDER BY a.defaultAccount DESC, a.name ASC
            """)
    List<AccountEntity> findByStatuses(@Param("statuses") List<AccountStatus> statuses);

    /**
     * Compared by the database collation, the same one the unique key on name uses.
     */
    boolean existsByName(String name);

    boolean existsByNameAndCodeNot(String name, UUID code);
}
