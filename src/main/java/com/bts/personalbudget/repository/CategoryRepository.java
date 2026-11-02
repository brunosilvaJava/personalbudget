package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    /**
     * Fetches the group too: every caller maps the category to a model that carries the group
     * code and name, and the listing would otherwise issue one query per row.
     */
    @Query("SELECT c FROM CategoryEntity c JOIN FETCH c.categoryGroup WHERE c.code = :code")
    Optional<CategoryEntity> findByCode(@Param("code") UUID code);

    @Query("""
            SELECT c FROM CategoryEntity c
            JOIN FETCH c.categoryGroup g
            WHERE c.status IN :statuses
            AND (:operationType IS NULL OR c.operationType = :operationType)
            AND (:groupCode IS NULL OR g.code = :groupCode)
            ORDER BY g.displayOrder ASC, c.name ASC
            """)
    List<CategoryEntity> findByFilters(
            @Param("operationType") OperationType operationType,
            @Param("statuses") List<CategoryStatus> statuses,
            @Param("groupCode") UUID groupCode
    );

    /**
     * Compared by the database collation, the same one the (name, operation_type) unique key uses,
     * so this check and the constraint agree on what counts as the same name.
     */
    boolean existsByNameAndOperationType(String name, OperationType operationType);

    boolean existsByNameAndOperationTypeAndCodeNot(String name, OperationType operationType, UUID code);

}
