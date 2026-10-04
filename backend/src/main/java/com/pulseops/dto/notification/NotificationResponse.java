package com.pulseops.dto.notification;

import com.pulseops.domain.notification.Notification;
import com.pulseops.domain.notification.NotificationType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        NotificationType type,
        boolean read,
        OffsetDateTime createdAt,
        UUID eventId,
        UUID systemId,
        UUID resourceId
) {
    public NotificationResponse(UUID id,String title,String message,NotificationType type,boolean read,OffsetDateTime createdAt){this(id,title,message,type,read,createdAt,null,null,null);}
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getTitle(), notification.getMessage(), notification.getType(),
                notification.isRead(), notification.getCreatedAt(),notification.getEventId(),notification.getSystemId(),notification.getResourceId());
    }
}
