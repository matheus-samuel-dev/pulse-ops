package com.pulseops.domain.integration;
import com.pulseops.domain.common.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name = "integration_connections")
public class IntegrationConnection extends AuditableEntity {
    @Column(name="owner_id", nullable=false) private UUID ownerId;
    @Column(nullable=false,length=40) private String slug;
    @Column(name="system_id") private UUID systemId;
    @Column(name="public_url",length=2048) private String publicUrl;
    @Column(name="action_path",nullable=false,length=512) private String actionPath;
    @JsonIgnore @Column(name="encrypted_token",columnDefinition="TEXT") private String encryptedToken;
    @Column(name="auto_dispatch",nullable=false) private boolean autoDispatch;
    @Column(name="base_url",nullable=false,length=2048) private String baseUrl;
    @Column(name="health_endpoint",nullable=false,length=512) private String healthEndpoint="/";
    @Column(name="timeout_ms",nullable=false) private int timeoutMs=10000;
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String v){baseUrl=v;}
    public String getHealthEndpoint(){return healthEndpoint;} public void setHealthEndpoint(String v){healthEndpoint=v;}
    public int getTimeoutMs(){return timeoutMs;} public void setTimeoutMs(int v){timeoutMs=v;}
    public UUID getOwnerId(){return ownerId;} public void setOwnerId(UUID v){ownerId=v;}
    public String getSlug(){return slug;} public void setSlug(String v){slug=v;}
    public UUID getSystemId(){return systemId;} public void setSystemId(UUID v){systemId=v;}
    public String getPublicUrl(){return publicUrl;} public void setPublicUrl(String v){publicUrl=v;}
    public String getActionPath(){return actionPath;} public void setActionPath(String v){actionPath=v;}
    public String getEncryptedToken(){return encryptedToken;} public void setEncryptedToken(String v){encryptedToken=v;}
    public boolean isAutoDispatch(){return autoDispatch;} public void setAutoDispatch(boolean v){autoDispatch=v;}
}
