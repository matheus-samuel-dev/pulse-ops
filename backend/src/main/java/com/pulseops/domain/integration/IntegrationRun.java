package com.pulseops.domain.integration;
import com.pulseops.domain.common.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="integration_runs")
public class IntegrationRun extends AuditableEntity {
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(name="connection_id",nullable=false) private UUID connectionId;
    @Column(name="system_id") private UUID systemId;
    @Column(name="system_name",nullable=false,length=120) private String systemName;
    @Column(nullable=false,length=40) private String slug;
    @Column(name="external_id",length=120) private String externalId;
    @Column(nullable=false,length=30) private String state;
    @Column(length=1000) private String message;
    @Column(name="report_url",length=2048) private String reportUrl;
    @Column(name="overall_score") private Integer overallScore;
    @Column(name="http_status") private Integer httpStatus;
    @Column(name="event_id") private UUID eventId;
    public UUID getOwnerId(){return ownerId;} public void setOwnerId(UUID v){ownerId=v;}
    public UUID getConnectionId(){return connectionId;} public void setConnectionId(UUID v){connectionId=v;}
    public UUID getSystemId(){return systemId;} public void setSystemId(UUID v){systemId=v;}
    public String getSystemName(){return systemName;} public void setSystemName(String v){systemName=v;}
    public String getSlug(){return slug;} public void setSlug(String v){slug=v;}
    public String getExternalId(){return externalId;} public void setExternalId(String v){externalId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getReportUrl(){return reportUrl;} public void setReportUrl(String v){reportUrl=v;}
    public Integer getOverallScore(){return overallScore;} public void setOverallScore(Integer v){overallScore=v;}
    public Integer getHttpStatus(){return httpStatus;} public void setHttpStatus(Integer v){httpStatus=v;}
    public UUID getEventId(){return eventId;} public void setEventId(UUID v){eventId=v;}
}
