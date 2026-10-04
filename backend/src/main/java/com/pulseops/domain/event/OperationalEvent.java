package com.pulseops.domain.event;

import com.pulseops.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "operational_events")
public class OperationalEvent extends AuditableEntity {
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "system_id") private UUID systemId;
    @Column(name = "system_name", nullable = false, length = 120) private String systemName;
    @Column(length = 20) private String environment;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 20) private String severity;
    @Column(nullable = false, length = 180) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, length = 100) private String source;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name="resource_id") private UUID resourceId;
    @Column(length=30) private String status;
    public UUID getResourceId(){return resourceId;}
    public void setResourceId(UUID value){resourceId=value;}
    public String getStatus(){return status;}
    public void setStatus(String value){status=value;}
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID value) { ownerId = value; }
    public UUID getSystemId() { return systemId; }
    public void setSystemId(UUID value) { systemId = value; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String value) { systemName = value; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String value) { environment = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getSeverity() { return severity; }
    public void setSeverity(String value) { severity = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime value) { occurredAt = value; }
}
