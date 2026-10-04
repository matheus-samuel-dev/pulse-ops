package com.pulseops.domain.integration;
import com.pulseops.domain.common.AuditableEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="integration_probes")
public class IntegrationProbe extends AuditableEntity {
    @Column(name="connection_id",nullable=false) private UUID connectionId;
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(nullable=false,length=40) private String slug;
    @Column(name="checked_at",nullable=false) private OffsetDateTime checkedAt;
    @Column(name="http_status") private Integer httpStatus;
    @Column(name="response_time_ms") private Long responseTimeMs;
    @Column(nullable=false) private boolean success;
    @Column(name="failure_type",nullable=false,length=30) private String failureType;
    @Column(nullable=false,length=512) private String message;
    public UUID getConnectionId(){return connectionId;} public void setConnectionId(UUID v){connectionId=v;}
    public UUID getOwnerId(){return ownerId;} public void setOwnerId(UUID v){ownerId=v;}
    public String getSlug(){return slug;} public void setSlug(String v){slug=v;}
    public OffsetDateTime getCheckedAt(){return checkedAt;} public void setCheckedAt(OffsetDateTime v){checkedAt=v;}
    public Integer getHttpStatus(){return httpStatus;} public void setHttpStatus(Integer v){httpStatus=v;}
    public Long getResponseTimeMs(){return responseTimeMs;} public void setResponseTimeMs(Long v){responseTimeMs=v;}
    public boolean isSuccess(){return success;} public void setSuccess(boolean v){success=v;}
    public String getFailureType(){return failureType;} public void setFailureType(String v){failureType=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
}
