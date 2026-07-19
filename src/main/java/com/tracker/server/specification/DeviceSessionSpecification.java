package com.tracker.server.specification;

import com.tracker.server.dto.DeviceSessionFilterRequest;
import com.tracker.server.entity.DeviceSession;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class DeviceSessionSpecification {

    public static Specification<DeviceSession> filter(DeviceSessionFilterRequest request) {
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
            if (request.getStartupTimeFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startupTime"), request.getStartupTimeFrom()));
            }

            if (request.getStartupTimeTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startupTime"), request.getStartupTimeTo()));
            }

            if (request.getShutdownTimeFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("shutdownTime"), request.getShutdownTimeFrom()));
            }

            if (request.getShutdownTimeTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("shutdownTime"), request.getShutdownTimeTo()));
            }

            // Global date range filters (applies to both startupTime and shutdownTime)
            if (request.getFromDate() != null && request.getEndDate() != null) {
                Predicate startInRange = criteriaBuilder.between(root.get("startupTime"), request.getFromDate(), request.getEndDate());
                Predicate endInRange = criteriaBuilder.between(root.get("shutdownTime"), request.getFromDate(), request.getEndDate());
                predicates.add(criteriaBuilder.or(startInRange, endInRange));
            } else {
                if (request.getFromDate() != null) {
                    Predicate startAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("startupTime"), request.getFromDate());
                    Predicate endAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("shutdownTime"), request.getFromDate());
                    predicates.add(criteriaBuilder.or(startAfter, endAfter));
                }

                if (request.getEndDate() != null) {
                    Predicate startBefore = criteriaBuilder.lessThanOrEqualTo(root.get("startupTime"), request.getEndDate());
                    Predicate endBefore = criteriaBuilder.lessThanOrEqualTo(root.get("shutdownTime"), request.getEndDate());
                    predicates.add(criteriaBuilder.or(startBefore, endBefore));
                }
            }

            if (request.getSessionDurationMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("sessionDurationSeconds"), request.getSessionDurationMin()));
            }

            if (request.getSessionDurationMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("sessionDurationSeconds"), request.getSessionDurationMax()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}