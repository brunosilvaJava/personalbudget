package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the filter of GET /financial_movement. A derived query cannot express the category
 * filter, which is "code IN (...)", "code IS NULL" or both at once.
 */
public final class FinancialMovementSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private FinancialMovementSpecifications() {
    }

    /**
     * @param operationTypes must not be empty; the caller defaults to every type
     * @param statuses       must not be empty; the caller defaults to every status
     * @param startDate      inclusive lower bound of movement_date
     * @param endDate        inclusive upper bound of movement_date
     * @param categoryCodes  keeps movements of these categories; null or empty does not filter
     * @param withoutCategory keeps movements that have no category; combined with categoryCodes
     *                        it keeps the movements that match either
     */
    public static Specification<FinancialMovementEntity> filter(final String description,
                                                                final List<OperationType> operationTypes,
                                                                final List<FinancialMovementStatus> statuses,
                                                                final LocalDateTime startDate,
                                                                final LocalDateTime endDate,
                                                                final List<UUID> categoryCodes,
                                                                final boolean withoutCategory) {
        return filter(description, operationTypes, statuses, startDate, endDate, categoryCodes, withoutCategory,
                null, false);
    }

    public static Specification<FinancialMovementEntity> filter(final String description,
                                                                final List<OperationType> operationTypes,
                                                                final List<FinancialMovementStatus> statuses,
                                                                final LocalDateTime startDate,
                                                                final LocalDateTime endDate,
                                                                final List<UUID> categoryCodes,
                                                                final boolean withoutCategory,
                                                                final UUID thirdPartyCode,
                                                                final boolean ownOnly) {
        return (root, query, criteriaBuilder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isTrue(root.get("flagActive")));
            predicates.add(root.get("operationType").in(operationTypes));
            predicates.add(root.get("status").in(statuses));
            predicates.add(criteriaBuilder.between(root.<LocalDateTime>get("movementDate"), startDate, endDate));

            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        root.<String>get("description"),
                        "%" + escapeLike(description) + "%",
                        LIKE_ESCAPE));
            }

            final boolean hasCategoryCodes = categoryCodes != null && !categoryCodes.isEmpty();
            if (hasCategoryCodes && withoutCategory) {
                predicates.add(criteriaBuilder.or(
                        root.get("categoryCode").in(categoryCodes),
                        criteriaBuilder.isNull(root.get("categoryCode"))));
            } else if (hasCategoryCodes) {
                predicates.add(root.get("categoryCode").in(categoryCodes));
            } else if (withoutCategory) {
                predicates.add(criteriaBuilder.isNull(root.get("categoryCode")));
                predicates.add(criteriaBuilder.isNull(root.get("receivableSourceMovementId")));
            }

            if (thirdPartyCode != null) {
                predicates.add(criteriaBuilder.equal(root.get("thirdParty").get("code"), thirdPartyCode));
            }
            if (ownOnly) {
                predicates.add(criteriaBuilder.isNull(root.get("thirdParty")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

}
