package com.tracker.server.specification;

import com.tracker.server.dto.IdleFilterRequest;
import com.tracker.server.entity.IdleActivity;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class IdleSpecification {

    public static Specification<IdleActivity> filter(IdleFilterRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), request.getUserId()));
            }

            if (request.getDeviceId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("device").get("id"), request.getDeviceId()));
            }

            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }

            // Individual date filters
            if (request.getIdleStartFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("idleStart"), request.getIdleStartFrom()));
            }

            if (request.getIdleStartTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("idleStart"), request.getIdleStartTo()));
            }

            if (request.getIdleEndFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("idleEnd"), request.getIdleEndFrom()));
            }

            if (request.getIdleEndTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("idleEnd"), request.getIdleEndTo()));
            }

            // Global date range filters (applies to both idleStart and idleEnd)
            if (request.getFromDate() != null && request.getEndDate() != null) {
                Predicate startInRange = criteriaBuilder.between(root.get("idleStart"), request.getFromDate(), request.getEndDate());
                Predicate endInRange = criteriaBuilder.between(root.get("idleEnd"), request.getFromDate(), request.getEndDate());
                predicates.add(criteriaBuilder.or(startInRange, endInRange));
            } else {
                if (request.getFromDate() != null) {
                    Predicate startAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("idleStart"), request.getFromDate());
                    Predicate endAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("idleEnd"), request.getFromDate());
                    predicates.add(criteriaBuilder.or(startAfter, endAfter));
                }

                if (request.getEndDate() != null) {
                    Predicate startBefore = criteriaBuilder.lessThanOrEqualTo(root.get("idleStart"), request.getEndDate());
                    Predicate endBefore = criteriaBuilder.lessThanOrEqualTo(root.get("idleEnd"), request.getEndDate());
                    predicates.add(criteriaBuilder.or(startBefore, endBefore));
                }
            }

            if (request.getIdleSecondsMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("idleSeconds"), request.getIdleSecondsMin()));
            }

            if (request.getIdleSecondsMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("idleSeconds"), request.getIdleSecondsMax()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}