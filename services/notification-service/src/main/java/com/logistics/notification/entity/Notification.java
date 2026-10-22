package com.logistics.notification.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    @Column(name = "channel", length = 20, nullable = false)
    private String channel;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "content", length = 1000)
    private String content;

    @Column(name = "platform_notification_id", length = 100)
    private String platformNotificationId;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notification() {
    }

    public Notification(String id, String recipient, String channel, String status,
                        String eventId, String eventType, String orderId,
                        String subject, String content, Instant sentAt, Instant createdAt) {
        this.id = id;
        this.recipient = recipient;
        this.channel = channel;
        this.status = status;
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.subject = subject;
        this.content = content;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getPlatformNotificationId() {
        return platformNotificationId;
    }

    public void setPlatformNotificationId(String platformNotificationId) {
        this.platformNotificationId = platformNotificationId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
