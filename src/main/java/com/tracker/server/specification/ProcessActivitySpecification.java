package com.tracker.server.specification;

import com.tracker.server.dto.ProcessFilterRequest;
import com.tracker.server.entity.ProcessActivity;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProcessActivitySpecification {

    public static Specification<ProcessActivity> filter(ProcessFilterRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), request.getUserId()));
            }

            if (request.getDeviceId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("device").get("id"), request.getDeviceId()));
            }

            if (request.getProcessName() != null && !request.getProcessName().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("processName")),
                    "%" + request.getProcessName().toLowerCase() + "%"
                ));
            }

            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }

            if (request.getPid() != null) {
                predicates.add(criteriaBuilder.equal(root.get("pid"), request.getPid()));
            }

            // Individual date filters
            if (request.getStartTimeFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startTime"), request.getStartTimeFrom()));
            }

            if (request.getStartTimeTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startTime"), request.getStartTimeTo()));
            }

            if (request.getEndTimeFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("endTime"), request.getEndTimeFrom()));
            }

            if (request.getEndTimeTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endTime"), request.getEndTimeTo()));
            }

            // Global date range filters (applies to both startTime and endTime)
            if (request.getFromDate() != null && request.getEndDate() != null) {
                // Records where startTime OR endTime falls within the range
                Predicate startInRange = criteriaBuilder.between(root.get("startTime"), request.getFromDate(), request.getEndDate());
                Predicate endInRange = criteriaBuilder.between(root.get("endTime"), request.getFromDate(), request.getEndDate());
                predicates.add(criteriaBuilder.or(startInRange, endInRange));
            } else {
                if (request.getFromDate() != null) {
                    Predicate startAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("startTime"), request.getFromDate());
                    Predicate endAfter = criteriaBuilder.greaterThanOrEqualTo(root.get("endTime"), request.getFromDate());
                    predicates.add(criteriaBuilder.or(startAfter, endAfter));
                }

                if (request.getEndDate() != null) {
                    Predicate startBefore = criteriaBuilder.lessThanOrEqualTo(root.get("startTime"), request.getEndDate());
                    Predicate endBefore = criteriaBuilder.lessThanOrEqualTo(root.get("endTime"), request.getEndDate());
                    predicates.add(criteriaBuilder.or(startBefore, endBefore));
                }
            }

            if (request.getDurationMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("durationSeconds"), request.getDurationMin()));
            }

            if (request.getDurationMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("durationSeconds"), request.getDurationMax()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}