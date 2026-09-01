package com.pulseops.dto.notification;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, long unreadCount) {
}
