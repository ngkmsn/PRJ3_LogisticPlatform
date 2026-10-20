package com.logistics.shipment.eligibility.pipeline;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline coordinator that sequentially applies registered {@link DriverEligibilityFilter} stages.
 * Automatically injects all CDI beans implementing {@link DriverEligibilityFilter} sorted by {@code getOrder()}.
 */
@ApplicationScoped
public class DriverEligibilityPipeline {

    private static final Logger log = LoggerFactory.getLogger(DriverEligibilityPipeline.class);

    private final List<DriverEligibilityFilter> filters = new ArrayList<>();

    public DriverEligibilityPipeline() {
    }

    @Inject
    public DriverEligibilityPipeline(Instance<DriverEligibilityFilter> filterInstances) {
        if (filterInstances != null) {
            for (DriverEligibilityFilter filter : filterInstances) {
                this.filters.add(filter);
            }
            this.filters.sort(Comparator.comparingInt(DriverEligibilityFilter::getOrder));
        }
    }

    public DriverEligibilityPipeline(List<DriverEligibilityFilter> filterList) {
        if (filterList != null) {
            this.filters.addAll(filterList);
            this.filters.sort(Comparator.comparingInt(DriverEligibilityFilter::getOrder));
        }
    }

    /**
     * Programmatically registers a filter stage.
     */
    public DriverEligibilityPipeline addFilter(DriverEligibilityFilter filter) {
        if (filter != null) {
            this.filters.add(filter);
            this.filters.sort(Comparator.comparingInt(DriverEligibilityFilter::getOrder));
        }
        return this;
    }

    /**
     * Executes the eligibility pipeline stages over the candidate pool.
     *
     * @param initialCandidates initial pool of candidate drivers
     * @param criteria          assignment requirements
     * @return filtered and evaluated eligible drivers
     */
    public List<EligibleDriver> execute(List<EligibleDriver> initialCandidates, AssignmentCriteria criteria) {
        if (initialCandidates == null || initialCandidates.isEmpty()) {
            log.info("Initial candidate pool is empty, pipeline execution skipped.");
            return Collections.emptyList();
        }

        List<EligibleDriver> current = new ArrayList<>(initialCandidates);
        String orderId = criteria != null ? criteria.getOrderId() : "N/A";
        log.info("Starting DriverEligibilityPipeline with {} initial candidates for order '{}'", current.size(), orderId);

        for (DriverEligibilityFilter filter : filters) {
            if (current.isEmpty()) {
                log.info("Candidate pool exhausted after stage '{}'. Halting pipeline execution early for order '{}'.",
                        filter.getName(), orderId);
                break;
            }

            int before = current.size();
            current = filter.filter(current, criteria);
            if (current == null) {
                current = Collections.emptyList();
            }
            log.debug("Pipeline stage '{}' executed for order '{}': {} -> {} candidates",
                    filter.getName(), orderId, before, current.size());
        }

        log.info("DriverEligibilityPipeline completed for order '{}': {} eligible candidates remaining.",
                orderId, current.size());
        return current;
    }

    public List<DriverEligibilityFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }
}
