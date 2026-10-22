package com.logistics.notification.client;

public interface NotificationPlatformClient {

    NotificationPlatformResult sendNotification(NotificationPlatformRequest request, String eventId);
}
