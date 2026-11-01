package com.logistics.shipment.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.dto.DeadLetterMetricsDto;
import com.logistics.shipment.entity.DeadLetterMessage;
import com.logistics.shipment.service.DeadLetterQueueService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterControllerTest {

    @Mock
    private DeadLetterQueueService deadLetterQueueService;

    private DeadLetterController controller;
    private ApiDeadLetterResource apiResource;

    @BeforeEach
    void setUp() {
        controller = new DeadLetterController();
        controller.deadLetterQueueService = deadLetterQueueService;

        apiResource = new ApiDeadLetterResource();
        apiResource.deadLetterController = controller;
    }

    @Test
    @DisplayName("listMessages: returns 200 OK with list of DeadLetterMessage")
    void listMessages_returnsList() {
        DeadLetterMessage msg = new DeadLetterMessage("evt-1", "topic-1", "ORDER_CREATED", "ord-1", "{}", "Ex", "Err", 1);
        when(deadLetterQueueService.listMessages("PENDING", "topic-1", 0, 20)).thenReturn(List.of(msg));

        Response response = controller.listMessages("PENDING", "topic-1", 0, 20);

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isInstanceOf(List.class);

        // Also verify ApiDeadLetterResource delegates properly
        Response apiResp = apiResource.listMessages("PENDING", "topic-1", 0, 20);
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("getMetrics: returns 200 OK with aggregated metrics")
    void getMetrics_returnsMetrics() {
        DeadLetterMetricsDto metrics = new DeadLetterMetricsDto(5, 10, 2);
        when(deadLetterQueueService.getMetrics()).thenReturn(metrics);

        Response response = controller.getMetrics();

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.getData()).isEqualTo(metrics);

        Response apiResp = apiResource.getMetrics();
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("getMessageById: returns 200 OK when message found")
    void getMessageById_found_returnsMessage() {
        DeadLetterMessage msg = new DeadLetterMessage("evt-1", "topic-1", "ORDER_CREATED", "ord-1", "{}", "Ex", "Err", 1);
        when(deadLetterQueueService.getById("dlq-1")).thenReturn(Optional.of(msg));

        Response response = controller.getMessageById("dlq-1");

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.getData()).isEqualTo(msg);

        Response apiResp = apiResource.getMessageById("dlq-1");
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("getMessageById: returns 404 NOT_FOUND when message not found")
    void getMessageById_notFound_returns404() {
        when(deadLetterQueueService.getById("missing")).thenReturn(Optional.empty());

        Response response = controller.getMessageById("missing");

        assertThat(response.getStatus()).isEqualTo(404);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("replayMessage: returns 200 OK when replay succeeds")
    void replayMessage_success_returns200() {
        when(deadLetterQueueService.replayMessage("dlq-1", "admin_user")).thenReturn(true);

        Response response = controller.replayMessage("dlq-1", "admin_user");

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();

        Response apiResp = apiResource.replayMessage("dlq-1", "admin_user");
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("replayMessage: returns 404 NOT_FOUND when message does not exist")
    void replayMessage_notFound_returns404() {
        when(deadLetterQueueService.replayMessage("missing", "OPERATOR")).thenReturn(false);

        Response response = controller.replayMessage("missing", null);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("replayMessage: returns 500 INTERNAL_SERVER_ERROR when replay throws error")
    void replayMessage_error_returns500() {
        when(deadLetterQueueService.replayMessage("dlq-err", "OPERATOR")).thenThrow(new RuntimeException("Consumer failed"));

        Response response = controller.replayMessage("dlq-err", null);

        assertThat(response.getStatus()).isEqualTo(500);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.getMessage()).contains("Failed to replay DLQ message");
    }

    @Test
    @DisplayName("discardMessage: returns 200 OK when discard succeeds")
    void discardMessage_success_returns200() {
        when(deadLetterQueueService.discardMessage("dlq-1", "Poison pill obsolete")).thenReturn(true);

        Response response = controller.discardMessage("dlq-1", "Poison pill obsolete");

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();

        Response apiResp = apiResource.discardMessage("dlq-1", "Poison pill obsolete");
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("discardMessage: returns 404 NOT_FOUND when message not found")
    void discardMessage_notFound_returns404() {
        when(deadLetterQueueService.discardMessage("missing", "Discard")).thenReturn(false);

        Response response = controller.discardMessage("missing", "Discard");

        assertThat(response.getStatus()).isEqualTo(404);
    }
}
