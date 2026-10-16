package com.logistics.shipment.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.InboxEvent;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.model.InboxStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.InboxEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class InboxEventServiceTest {

    @Inject
    InboxEventService inboxEventService;

    @Inject
    InboxEventRepository inboxEventRepository;

    @Inject
    AssignmentRepository assignmentRepository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        inboxEventRepository.deleteAll();
        assignmentRepository.deleteAll();
    }

    private OrderCreatedPayload createPayload(String orderId) {
        return new OrderCreatedPayload(
                orderId,
                "ORD-" + orderId.substring(0, 8),
                "cust-uuid-999",
                "Hoàng Văn E",
                "0977889900",
                "12 Võ Văn Kiệt, Quận 1, TP.HCM",
                OrderStatus.PENDING,
                BigDecimal.valueOf(180000.00),
                "Hàng mẫu thí nghiệm",
                Instant.now()
        );
    }

    @Test
    @DisplayName("First-time event is processed and atomically records in inbox_events with PROCESSED status")
    void processOrderCreatedWithInbox_firstTime_success() {
        String eventId = "evt-inbox-first-" + UUID.randomUUID();
        String orderId = "ord-inbox-first-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        Assignment assignment = inboxEventService.processOrderCreatedWithInbox(
                eventId, "ORDER_CREATED", orderId, payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PENDING);

        // Verify inbox record
        Optional<InboxEvent> inboxEvent = inboxEventService.findByEventId(eventId);
        assertThat(inboxEvent).isPresent();
        assertThat(inboxEvent.get().getEventId()).isEqualTo(eventId);
        assertThat(inboxEvent.get().getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(inboxEvent.get().getAggregateId()).isEqualTo(orderId);
        assertThat(inboxEvent.get().getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(inboxEvent.get().getProcessedAt()).isNotNull();

        // Verify isAlreadyProcessed returns true
        assertThat(inboxEventService.isAlreadyProcessed(eventId)).isTrue();
    }

    @Test
    @DisplayName("Duplicate event with same eventId is skipped without re-executing business operation")
    void processOrderCreatedWithInbox_duplicateEvent_skipped() {
        String eventId = "evt-inbox-dup-" + UUID.randomUUID();
        String orderId = "ord-inbox-dup-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        // First execution
        Assignment first = inboxEventService.processOrderCreatedWithInbox(
                eventId, "ORDER_CREATED", orderId, payload);
        assertThat(first).isNotNull();

        // Second execution (duplicate event redelivery from broker)
        Assignment second = inboxEventService.processOrderCreatedWithInbox(
                eventId, "ORDER_CREATED", orderId, payload);
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());

        // Verify counts in DB
        assertThat(assignmentRepository.count()).isEqualTo(1L);
        assertThat(inboxEventRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Atomic rollback: If business operation fails, inbox record is NOT committed")
    void processOrderCreatedWithInbox_businessFailure_rollsBackBoth() {
        String eventId = "evt-inbox-fail-" + UUID.randomUUID();
        String orderId = "ord-inbox-fail-" + UUID.randomUUID();
        OrderCreatedPayload invalidPayload = new OrderCreatedPayload(); // null orderId causes failure

        assertThatThrownBy(() -> inboxEventService.processOrderCreatedWithInbox(
                eventId, "ORDER_CREATED", orderId, invalidPayload))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify nothing committed to inbox or assignments
        assertThat(inboxEventService.isAlreadyProcessed(eventId)).isFalse();
        assertThat(inboxEventRepository.findByEventId(eventId)).isEmpty();
        assertThat(assignmentRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Concurrent duplicate events: Handled gracefully via DB unique constraint without throwing errors")
    void processOrderCreatedWithInbox_concurrentDuplicates_handledSmoothly() throws Exception {
        String eventId = "evt-inbox-concurrent-" + UUID.randomUUID();
        String orderId = "ord-inbox-concurrent-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Assignment res = inboxEventService.processOrderCreatedWithInbox(
                            eventId, "ORDER_CREATED", orderId, payload);
                    if (res != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected error handled gracefully
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // trigger both threads simultaneously
        doneLatch.await();
        executor.shutdown();

        // Both threads must complete successfully (one created, other gracefully returned existing)
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(assignmentRepository.count()).isEqualTo(1L);
        assertThat(inboxEventRepository.count()).isEqualTo(1L);
    }
}
