package com.logistics.shipment.service;

import com.logistics.common.security.AuthPrincipal;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.AssignmentHistoryDto;
import com.logistics.shipment.dto.CreateAssignmentRequest;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.dto.ReassignDriverRequest;
import com.logistics.shipment.dto.UnassignedOrderDto;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.service.DriverSelectionService;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.exception.DuplicateAssignmentException;
import com.logistics.shipment.model.AssignmentActionType;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.publisher.DriverAssignedEventPublisher;
import com.logistics.shipment.repository.AssignmentHistoryRepository;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@DefaultBean
public class DefaultAssignmentService implements AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssignmentService.class);

    public static final String REASON_NO_ELIGIBLE_DRIVER = "NO_ELIGIBLE_DRIVER";
    public static final String REASON_ALL_DRIVERS_BUSY = "ALL_DRIVERS_BUSY";

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

    @Inject
    DriverSelectionService driverSelectionService;

    @Inject
    DriverAssignedEventPublisher eventPublisher;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    DriverClient driverClient;

    @ConfigProperty(name = "logistics.assignment.driver.workload.hard-limit", defaultValue = "3")
    int defaultHardLimit = 3;

    public DefaultAssignmentService() {
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService) {
        this.assignmentRepository = assignmentRepository;
        this.driverSelectionService = driverSelectionService;
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    DriverAssignedEventPublisher eventPublisher) {
        this.assignmentRepository = assignmentRepository;
        this.driverSelectionService = driverSelectionService;
        this.eventPublisher = eventPublisher;
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    DriverAssignedEventPublisher eventPublisher,
                                    int defaultHardLimit) {
        this.assignmentRepository = assignmentRepository;
        this.driverSelectionService = driverSelectionService;
        this.eventPublisher = eventPublisher;
        this.defaultHardLimit = defaultHardLimit;
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    int defaultHardLimit) {
        this.assignmentRepository = assignmentRepository;
        this.driverSelectionService = driverSelectionService;
        this.defaultHardLimit = defaultHardLimit;
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    DriverAssignedEventPublisher eventPublisher,
                                    JwtTokenProvider jwtTokenProvider) {
        this(assignmentRepository, driverSelectionService, eventPublisher, jwtTokenProvider, null, null);
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    DriverAssignedEventPublisher eventPublisher,
                                    JwtTokenProvider jwtTokenProvider,
                                    DriverClient driverClient) {
        this(assignmentRepository, driverSelectionService, eventPublisher, jwtTokenProvider, driverClient, null);
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository,
                                    DriverSelectionService driverSelectionService,
                                    DriverAssignedEventPublisher eventPublisher,
                                    JwtTokenProvider jwtTokenProvider,
                                    DriverClient driverClient,
                                    AssignmentHistoryRepository assignmentHistoryRepository) {
        this.assignmentRepository = assignmentRepository;
        this.driverSelectionService = driverSelectionService;
        this.eventPublisher = eventPublisher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.driverClient = driverClient;
        this.assignmentHistoryRepository = assignmentHistoryRepository;
    }

    @Override
    @Transactional
    public Assignment initiateAssignment(OrderCreatedPayload payload) {
        if (payload == null || payload.getOrderId() == null) {
            throw new IllegalArgumentException("Payload and orderId must not be null");
        }

        // Idempotency check: if assignment already exists for this orderId, return existing
        Optional<Assignment> existing = assignmentRepository.findByOrderId(payload.getOrderId());
        if (existing.isPresent()) {
            Assignment found = existing.get();
            log.info("Assignment already exists for orderId '{}' (assignmentId: '{}', status: '{}'). Idempotent return.",
                    payload.getOrderId(), found.getId(), found.getStatus());
            return found;
        }

        Instant now = Instant.now();
        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(),
                payload.getOrderId(),
                payload.getOrderNumber(),
                payload.getCustomerId(),
                payload.getRecipientName(),
                payload.getRecipientPhone(),
                payload.getDeliveryAddress(),
                payload.getTotalAmount(),
                AssignmentStatus.PENDING,
                null,
                null, // assignedAt is null for initial PENDING state
                payload.getNotes(),
                now,
                now
        );

        assignmentRepository.persist(assignment);
        log.info("Successfully initiated assignment '{}' for orderId '{}' (orderNumber: '{}', status: '{}')",
                assignment.getId(), assignment.getOrderId(), assignment.getOrderNumber(), assignment.getStatus());

        return assignment;
    }

    @Override
    @Transactional
    public Assignment createAssignment(CreateAssignmentRequest request) {
        if (request == null || request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("CreateAssignmentRequest and orderId must not be null or empty");
        }
        if (request.getDriverId() == null || request.getDriverId().trim().isEmpty()) {
            throw new IllegalArgumentException("driverId must not be null or empty when creating an assignment");
        }
        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("customerId must not be null or empty");
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("deliveryAddress must not be null or empty");
        }

        String orderId = request.getOrderId().trim();
        String driverId = request.getDriverId().trim();

        // Check data integrity: verify if active assignment already exists for this order
        Optional<Assignment> existing = assignmentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Assignment found = existing.get();

            // If already ASSIGNED:
            if (found.getStatus() == AssignmentStatus.ASSIGNED) {
                if (driverId.equals(found.getDriverId())) {
                    log.info("Assignment for orderId '{}' is already assigned to driver '{}' (id: '{}'). Idempotent return.",
                            orderId, driverId, found.getId());
                    // Idempotent duplicate call: DO NOT re-publish event
                    return found;
                } else {
                    log.warn("Active assignment conflict: orderId '{}' is already assigned to driver '{}', cannot re-assign to '{}'",
                            orderId, found.getDriverId(), driverId);
                    throw new DuplicateAssignmentException(orderId, found.getId(),
                            "Order '" + orderId + "' already has an active assignment to driver '" + found.getDriverId() + "'");
                }
            }

            // If assignment exists in PENDING, SEARCHING_DRIVER, UNASSIGNED, CANCELLED, or FAILED: transition to ASSIGNED
            Instant now = Instant.now();
            found.setDriverId(driverId);
            found.setStatus(AssignmentStatus.ASSIGNED);
            found.setAssignedAt(now);
            found.setUnassignedReason(null);
            found.setUnassignedAt(null);
            found.setUpdatedAt(now);
            if (request.getNotes() != null) {
                found.setNotes(request.getNotes());
            }

            log.info("Updated existing assignment '{}' for orderId '{}' to ASSIGNED with driverId '{}'",
                    found.getId(), orderId, driverId);

            // Broadcast DRIVER_ASSIGNED event upon successful transition
            publishDriverAssignedEvent(found);
            recordHistory(found.getOrderId(), found.getId(), null, driverId, AssignmentActionType.MANUAL_ASSIGN, request.getNotes(), "SYSTEM");
            return found;
        }

        // Create a completely new Assignment record with driver linkage
        Instant now = Instant.now();
        Assignment newAssignment = new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                request.getOrderNumber(),
                request.getCustomerId(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getDeliveryAddress(),
                request.getTotalAmount(),
                AssignmentStatus.ASSIGNED,
                driverId,
                now, // assignedAt
                request.getNotes(),
                now,
                now
        );

        assignmentRepository.persist(newAssignment);
        log.info("Successfully created and persisted new Assignment '{}' for orderId '{}' with driverId '{}'",
                newAssignment.getId(), orderId, driverId);

        // Broadcast DRIVER_ASSIGNED event upon successful creation
        publishDriverAssignedEvent(newAssignment);
        recordHistory(newAssignment.getOrderId(), newAssignment.getId(), null, driverId, AssignmentActionType.MANUAL_ASSIGN, request.getNotes(), "SYSTEM");
        return newAssignment;
    }

    @Override
    @Transactional
    public Assignment assignDriver(String orderId, String driverId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }
        if (driverId == null || driverId.trim().isEmpty()) {
            throw new IllegalArgumentException("driverId must not be null or empty");
        }

        Assignment assignment = assignmentRepository.findByOrderId(orderId.trim())
                .orElseThrow(() -> new IllegalStateException("Assignment not found for orderId: " + orderId));

        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            if (driverId.trim().equals(assignment.getDriverId())) {
                log.info("Assignment '{}' for order '{}' is already ASSIGNED to driver '{}'. Idempotent return.",
                        assignment.getId(), orderId, driverId);
                // Idempotent duplicate call: DO NOT re-publish event
                return assignment;
            } else {
                throw new DuplicateAssignmentException(orderId, assignment.getId(),
                        "Order '" + orderId + "' already has an active assignment to driver '" + assignment.getDriverId() + "'");
            }
        }

        Instant now = Instant.now();
        assignment.setDriverId(driverId.trim());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(now);
        assignment.setUnassignedReason(null);
        assignment.setUnassignedAt(null);
        assignment.setUpdatedAt(now);

        log.info("Assigned driver '{}' to assignment '{}' for order '{}' (assignedAt: {})",
                driverId, assignment.getId(), orderId, now);

        // Broadcast DRIVER_ASSIGNED event upon successful assignment
        publishDriverAssignedEvent(assignment);
        recordHistory(assignment.getOrderId(), assignment.getId(), null, driverId.trim(), AssignmentActionType.MANUAL_ASSIGN, null, "SYSTEM");
        return assignment;
    }

    @Override
    @Transactional
    public Assignment autoAssignOrder(OrderCreatedPayload payload) {
        if (payload == null || payload.getOrderId() == null) {
            throw new IllegalArgumentException("Payload and orderId must not be null");
        }

        Assignment assignment = initiateAssignment(payload);

        AssignmentCriteria criteria = AssignmentCriteria.builder()
                .orderId(payload.getOrderId())
                .orderNumber(payload.getOrderNumber())
                .pickupLocation(payload.getDeliveryAddress())
                .deliveryAddress(payload.getDeliveryAddress())
                .totalAmount(payload.getTotalAmount())
                .build();

        return autoAssignOrder(payload.getOrderId(), criteria);
    }

    @Override
    @Transactional
    public Assignment autoAssignOrder(String orderId, AssignmentCriteria criteria) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }

        Assignment assignment = assignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Assignment not found for orderId: " + orderId));

        // Idempotency: if already assigned, skip re-assignment
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED && assignment.getDriverId() != null) {
            log.info("Assignment '{}' for order '{}' is already ASSIGNED to driver '{}'. Idempotent skip.",
                    assignment.getId(), orderId, assignment.getDriverId());
            return assignment;
        }

        if (driverSelectionService == null) {
            log.warn("DriverSelectionService is not configured. Falling back to SEARCHING_DRIVER for order '{}'", orderId);
            assignment.setStatus(AssignmentStatus.SEARCHING_DRIVER);
            assignment.setUpdatedAt(Instant.now());
            return assignment;
        }

        // Rank eligible candidate drivers
        List<EligibleDriver> rankedDrivers = driverSelectionService.rankEligibleDrivers(criteria);

        if (rankedDrivers.isEmpty()) {
            Instant now = Instant.now();
            assignment.setStatus(AssignmentStatus.UNASSIGNED);
            assignment.setDriverId(null);
            assignment.setUnassignedReason(REASON_NO_ELIGIBLE_DRIVER);
            assignment.setUnassignedAt(now);
            assignment.setUpdatedAt(now);

            log.warn("Auto-assignment: No eligible drivers qualified for orderId '{}'. Setting status to UNASSIGNED (reason: {}).",
                    orderId, REASON_NO_ELIGIBLE_DRIVER);
            return assignment;
        }

        // Select the best candidate that still satisfies concurrent workload constraints at commit time
        boolean assigned = false;
        for (EligibleDriver candidate : rankedDrivers) {
            if (candidate == null || candidate.getDriverId() == null) {
                continue;
            }

            int driverHardLimit = resolveCandidateHardLimit(candidate);
            long activeWorkload = assignmentRepository.countActiveAssignmentsByDriverId(candidate.getDriverId());

            if (activeWorkload < driverHardLimit) {
                Instant now = Instant.now();
                assignment.setDriverId(candidate.getDriverId());
                assignment.setStatus(AssignmentStatus.ASSIGNED);
                assignment.setAssignedAt(now);
                assignment.setUnassignedReason(null);
                assignment.setUnassignedAt(null);
                assignment.setUpdatedAt(now);
                assigned = true;

                log.info("Successfully assigned order '{}' to best-fit driver '{}' (activeWorkload: {}/{}, score: {}, assignedAt: {})",
                        orderId, candidate.getDriverId(), activeWorkload, driverHardLimit, candidate.getScore(), now);

                // Record history for auto-assignment
                recordHistory(orderId, assignment.getId(), null, candidate.getDriverId(),
                        AssignmentActionType.AUTO_ASSIGN,
                        "Auto assignment selection based on ranking score " + candidate.getScore(),
                        "SYSTEM");

                // Broadcast DRIVER_ASSIGNED event upon successful auto-assignment
                publishDriverAssignedEvent(assignment);
                break;
            } else {
                log.warn("Candidate driver '{}' exceeded hard limit under concurrent assignment (activeWorkload: {}, limit: {}). Evaluating next candidate.",
                        candidate.getDriverId(), activeWorkload, driverHardLimit);
            }
        }

        if (!assigned) {
            Instant now = Instant.now();
            assignment.setStatus(AssignmentStatus.UNASSIGNED);
            assignment.setDriverId(null);
            assignment.setUnassignedReason(REASON_ALL_DRIVERS_BUSY);
            assignment.setUnassignedAt(now);
            assignment.setUpdatedAt(now);

            log.warn("Auto-assignment: All {} candidate drivers exceeded capacity at commit time for orderId '{}'. Setting status to UNASSIGNED (reason: {}).",
                    rankedDrivers.size(), orderId, REASON_ALL_DRIVERS_BUSY);
        }

        return assignment;
    }

    private void publishDriverAssignedEvent(Assignment assignment) {
        if (eventPublisher == null || assignment == null || assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            return;
        }
        try {
            eventPublisher.publishDriverAssigned(assignment);
        } catch (Exception e) {
            log.error("Failed to broadcast DRIVER_ASSIGNED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    private int resolveCandidateHardLimit(EligibleDriver driver) {
        if (driver.getMetadata() != null) {
            Object customLimit = driver.getMetadata().get("hardLimit");
            if (customLimit == null) {
                customLimit = driver.getMetadata().get("maxOrders");
            }
            if (customLimit instanceof Number n) {
                return n.intValue();
            }
            if (customLimit instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultHardLimit > 0 ? defaultHardLimit : 3;
    }

    @Override
    public Optional<Assignment> findByOrderId(String orderId) {
        return assignmentRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<Assignment> findById(String id) {
        return assignmentRepository.findByIdOptional(id);
    }

    @Override
    public PagedResponse<UnassignedOrderDto> getUnassignedOrders(String authHeader, int page, int size, String status, String sort) {
        resolveAuthenticatedOperations(authHeader, "view unassigned orders");

        AssignmentStatus targetStatus = AssignmentStatus.UNASSIGNED;
        if (status != null && !status.trim().isEmpty()) {
            try {
                targetStatus = AssignmentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter '{}', defaulting to UNASSIGNED", status);
            }
        }

        PagedResponse<Assignment> pagedAssignments = assignmentRepository.findUnassigned(targetStatus, page, size, sort);
        List<UnassignedOrderDto> dtos = pagedAssignments.getContent().stream()
                .map(UnassignedOrderDto::from)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                dtos,
                pagedAssignments.getPage(),
                pagedAssignments.getSize(),
                pagedAssignments.getTotalElements(),
                pagedAssignments.getTotalPages()
        );
    }

    @Override
    public UnassignedOrderDto getUnassignedOrderDetail(String authHeader, String orderId) {
        resolveAuthenticatedOperations(authHeader, "view unassigned order detail");

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }

        Assignment assignment = assignmentRepository.findUnassignedByOrderId(orderId.trim())
                .orElseThrow(() -> new AssignmentNotFoundException("No unassigned order found with orderId: " + orderId));

        return UnassignedOrderDto.from(assignment);
    }

    public AuthPrincipal resolveAuthenticatedOperations(String authHeader, String action) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (jwtTokenProvider == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }

        if (principal.getRole() != Role.ADMIN && principal.getRole() != Role.DISPATCHER) {
            log.warn("Access denied: caller role '{}' is not ADMIN or DISPATCHER for {}", principal.getRole(), action);
            throw new AccessDeniedException("Access denied: Only administrators and dispatchers are permitted to " + action);
        }

        return principal;
    }

    @Override
    @Transactional
    public Assignment manualAssignDriver(String authHeader, String orderId, ManualAssignDriverRequest request) {
        AuthPrincipal operator = resolveAuthenticatedOperations(authHeader, "perform manual assignment");

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }
        if (request == null || request.getDriverId() == null || request.getDriverId().trim().isEmpty()) {
            throw new IllegalArgumentException("driverId must not be null or empty");
        }

        String targetOrderId = orderId.trim();
        String targetDriverId = request.getDriverId().trim();

        // 1. Verify Order exists in assignments
        Assignment assignment = assignmentRepository.findByOrderId(targetOrderId)
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment record found for orderId: " + targetOrderId));

        // 2. Validate Order state:
        // Already ASSIGNED -> reject with 409 DuplicateAssignmentException (strictly no reassignment in PB-035)
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            throw new DuplicateAssignmentException(targetOrderId, assignment.getId(),
                    "Order '" + targetOrderId + "' is already assigned to driver '" + assignment.getDriverId() + "'. Manual reassignment is not supported.");
        }

        // Final state (CANCELLED, FAILED) -> reject with 400 IllegalStateException
        if (assignment.getStatus() == AssignmentStatus.CANCELLED || assignment.getStatus() == AssignmentStatus.FAILED) {
            throw new IllegalStateException("Cannot manually assign driver to order in final status: " + assignment.getStatus());
        }

        // 3. Validate Driver via DriverClient
        if (driverClient != null) {
            Optional<EligibleDriver> driverOpt = driverClient.findDriverById(targetDriverId);
            if (driverOpt.isEmpty()) {
                throw new IllegalArgumentException("Driver '" + targetDriverId + "' does not exist or is not available for manual assignment");
            }
        }

        // 4. Update Assignment
        Instant now = Instant.now();
        assignment.setDriverId(targetDriverId);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(now);
        assignment.setAssignmentSource("MANUAL");
        assignment.setAssignedBy(operator.getUsername() != null ? operator.getUsername() : operator.getUserId());
        assignment.setUnassignedReason(null);
        assignment.setUnassignedAt(null);
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            String existingNotes = assignment.getNotes() != null ? assignment.getNotes() + "; " : "";
            assignment.setNotes(existingNotes + "[Manual Assign] " + request.getNotes().trim());
        }
        assignment.setUpdatedAt(now);

        log.info("Successfully manually assigned order '{}' to driver '{}' by operator '{}' ({})",
                targetOrderId, targetDriverId, assignment.getAssignedBy(), operator.getRole());

        // Record history for manual assignment
        recordHistory(targetOrderId, assignment.getId(), null, targetDriverId,
                AssignmentActionType.MANUAL_ASSIGN,
                request.getNotes(),
                assignment.getAssignedBy());

        // 5. Broadcast DRIVER_ASSIGNED event (PB-032 reuse)
        publishDriverAssignedEvent(assignment);

        return assignment;
    }

    @Override
    @Transactional
    public Assignment reassignDriver(String authHeader, String orderId, ReassignDriverRequest request) {
        AuthPrincipal operator = resolveAuthenticatedOperations(authHeader, "perform driver reassignment");

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }
        if (request == null || request.getNewDriverId() == null || request.getNewDriverId().trim().isEmpty()) {
            throw new IllegalArgumentException("newDriverId must not be null or empty");
        }

        String targetOrderId = orderId.trim();
        String newDriverId = request.getNewDriverId().trim();

        // 1. Verify Order exists in assignments
        Assignment assignment = assignmentRepository.findByOrderId(targetOrderId)
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment record found for orderId: " + targetOrderId));

        // 2. Validate Order state:
        // Must currently be in ASSIGNED status with a non-null driverId
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED || assignment.getDriverId() == null || assignment.getDriverId().trim().isEmpty()) {
            throw new IllegalStateException("Cannot reassign driver: order '" + targetOrderId + "' does not have an active assignment (status: " + assignment.getStatus() + ")");
        }

        // Final state (CANCELLED, FAILED) -> reject with IllegalStateException
        if (assignment.getStatus() == AssignmentStatus.CANCELLED || assignment.getStatus() == AssignmentStatus.FAILED) {
            throw new IllegalStateException("Cannot reassign driver for order in final status: " + assignment.getStatus());
        }

        String previousDriverId = assignment.getDriverId().trim();

        // 3. New driver must differ from current driver
        if (previousDriverId.equals(newDriverId)) {
            throw new IllegalArgumentException("New driver cannot be the same as the currently assigned driver ('" + previousDriverId + "')");
        }

        // 4. Validate new Driver via DriverClient
        if (driverClient != null) {
            Optional<EligibleDriver> driverOpt = driverClient.findDriverById(newDriverId);
            if (driverOpt.isEmpty()) {
                throw new IllegalArgumentException("Driver '" + newDriverId + "' does not exist or is not available for assignment");
            }
        }

        // 5. Transactional update of Assignment
        Instant now = Instant.now();
        assignment.setPreviousDriverId(previousDriverId);
        assignment.setDriverId(newDriverId);
        assignment.setReassignReason(request.getReason() != null ? request.getReason().trim() : null);
        assignment.setReassignedAt(now);
        assignment.setAssignedAt(now);
        assignment.setAssignmentSource("MANUAL");

        String operatorIdentifier = operator.getUsername() != null ? operator.getUsername() : operator.getUserId();
        assignment.setReassignedBy(operatorIdentifier);
        assignment.setAssignedBy(operatorIdentifier);

        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            String existingNotes = assignment.getNotes() != null ? assignment.getNotes() + "; " : "";
            assignment.setNotes(existingNotes + "[Reassigned] " + request.getNotes().trim());
        }
        assignment.setUpdatedAt(now);

        log.info("Successfully reassigned order '{}' from driver '{}' to '{}' by operator '{}' (reason: '{}')",
                targetOrderId, previousDriverId, newDriverId, operatorIdentifier, assignment.getReassignReason());

        // Record history for reassignment
        recordHistory(targetOrderId, assignment.getId(), previousDriverId, newDriverId,
                AssignmentActionType.REASSIGN,
                assignment.getReassignReason(),
                operatorIdentifier);

        // 6. Broadcast DRIVER_REASSIGNED and DRIVER_ASSIGNED events
        publishDriverReassignedEvent(assignment, previousDriverId, assignment.getReassignReason());

        return assignment;
    }

    private void publishDriverReassignedEvent(Assignment assignment, String previousDriverId, String reassignReason) {
        if (eventPublisher == null || assignment == null) {
            return;
        }
        try {
            eventPublisher.publishDriverReassigned(assignment, previousDriverId, reassignReason);
        } catch (Exception e) {
            log.error("Failed to broadcast DRIVER_REASSIGNED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    private void recordHistory(String orderId, String assignmentId, String previousDriverId,
                               String newDriverId, AssignmentActionType actionType,
                               String reason, String performedBy) {
        if (assignmentHistoryRepository == null) {
            log.warn("AssignmentHistoryRepository is not configured; skipping history recording.");
            return;
        }
        AssignmentHistory history = new AssignmentHistory(
                orderId,
                assignmentId,
                previousDriverId,
                newDriverId,
                actionType,
                reason,
                performedBy != null && !performedBy.trim().isEmpty() ? performedBy.trim() : "SYSTEM"
        );
        assignmentHistoryRepository.persist(history);
        log.info("Recorded assignment history: action={}, orderId={}, assignmentId={}, prevDriver={}, newDriver={}, by={}",
                actionType, orderId, assignmentId, previousDriverId, newDriverId, history.getPerformedBy());
    }

    @Override
    public List<AssignmentHistoryDto> getAssignmentHistory(String authHeader, String orderId) {
        resolveAuthenticatedOperations(authHeader, "view assignment history");

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }

        String targetOrderId = orderId.trim();
        // Check assignment exists for this order
        assignmentRepository.findByOrderId(targetOrderId)
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment record found for orderId: " + targetOrderId));

        if (assignmentHistoryRepository == null) {
            return List.of();
        }

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc(targetOrderId);
        return histories.stream()
                .map(AssignmentHistoryDto::from)
                .collect(Collectors.toList());
    }
}
