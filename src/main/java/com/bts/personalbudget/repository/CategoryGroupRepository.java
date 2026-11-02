package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryGroupRepository extends JpaRepository<CategoryGroupEntity, Long> {

    Optional<CategoryGroupEntity> findByCode(UUID code);

    List<CategoryGroupEntity> findAllByOrderByDisplayOrderAscNameAsc();

}
