package com.pulseops.domain.notification;

import com.pulseops.domain.common.AuditableEntity;
import com.pulseops.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_read_created", columnList = "user_id,is_read,created_at"),
                @Index(name = "idx_notifications_type", columnList = "type")
        }
)
public class Notification extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String title;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type = NotificationType.INFO;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name="event_id") private java.util.UUID eventId;
    @Column(name="system_id") private java.util.UUID systemId;
    @Column(name="resource_id") private java.util.UUID resourceId;
    public java.util.UUID getEventId(){return eventId;}
    public void setEventId(java.util.UUID v){eventId=v;}
    public java.util.UUID getSystemId(){return systemId;}
    public void setSystemId(java.util.UUID v){systemId=v;}
    public java.util.UUID getResourceId(){return resourceId;}
    public void setResourceId(java.util.UUID v){resourceId=v;}
    public Notification() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
