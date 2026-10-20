package com.logistics.shipment.eligibility.pipeline;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;

import java.util.List;

/**
 * Pluggable stage in the Driver Eligibility Pipeline.
 * Follows the Pipeline / Chain of Responsibility pattern, allowing subsequent stages
 * (availability filter, workload balancing, geodistance ranking) to be appended cleanly.
 */
public interface DriverEligibilityFilter {

    /**
     * Filters and/or enriches candidate drivers according to stage-specific criteria.
     *
     * @param candidates current candidate drivers
     * @param criteria   assignment criteria from order
     * @return filtered/enriched candidate list (never null)
     */
    List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria);

    /**
     * Order of execution in the pipeline. Lower values execute earlier.
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Descriptive name of this filter stage for logging and observability.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
