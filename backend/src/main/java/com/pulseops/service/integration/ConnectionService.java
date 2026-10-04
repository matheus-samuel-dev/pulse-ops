package com.pulseops.service.integration;
import com.pulseops.domain.integration.IntegrationConnection;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.integration.ConnectionRequest;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.IntegrationConnectionRepository;
import com.pulseops.security.AccountScope;
import com.pulseops.security.IntegrationCredentialCipher;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.service.EventRecorder;
import com.pulseops.service.MonitoredSystemService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {
    private final IntegrationConnectionRepository connections;
    private final MonitoredSystemService systems;
    private final MonitoredUrlPolicy policy;
    private final IntegrationCredentialCipher cipher;
    private final EventRecorder events;
    public ConnectionService(IntegrationConnectionRepository connections,MonitoredSystemService systems,
            MonitoredUrlPolicy policy,IntegrationCredentialCipher cipher,EventRecorder events) {
        this.connections=connections;this.systems=systems;this.policy=policy;this.cipher=cipher;this.events=events;
    }
    @Transactional(readOnly=true)
    public ConnectionView get(String slug) { IntegrationCatalog.find(slug);return connections.findByOwnerIdAndSlug(AccountScope.userId(),slug).map(ConnectionView::from).orElse(null); }
    @Transactional
    public ConnectionView save(String slug,ConnectionRequest input) {
        IntegrationCatalog.find(slug);
        MonitoredSystem legacy=input.systemId()==null?null:systems.findEntity(input.systemId());
        String baseUrl=input.baseUrl()==null||input.baseUrl().isBlank()?legacy==null?null:legacy.getBaseUrl():input.baseUrl();
        if(baseUrl==null)throw new BusinessRuleException("Informe a URL base do serviço da integração");
        String endpoint=input.healthEndpoint()==null?legacy==null?"/":legacy.getHealthEndpoint():input.healthEndpoint();
        var destination=policy.validate(baseUrl,endpoint);
        String path=policy.validate(destination.baseUrl(),input.actionPath()).healthEndpoint();
        String publicUrl=IntegrationService.publicOrigin(input.publicUrl());
        if(input.publicUrl()!=null && !input.publicUrl().isBlank() && publicUrl==null) throw new BusinessRuleException("Informe uma URL pública HTTP ou HTTPS sem credenciais ou parâmetros");
        if(input.autoDispatch() && !slug.equals("nexus-flow")) throw new BusinessRuleException("Somente o Nexus Flow recebe eventos automáticos");
        IntegrationConnection connection=connections.findByOwnerIdAndSlug(AccountScope.userId(),slug).orElseGet(IntegrationConnection::new);
        connection.setOwnerId(AccountScope.userId());connection.setSlug(slug);connection.setSystemId(legacy==null?null:legacy.getId());
        connection.setBaseUrl(destination.baseUrl());connection.setHealthEndpoint(destination.healthEndpoint());connection.setTimeoutMs(input.timeoutMs()==null?10000:input.timeoutMs());
        connection.setActionPath(path);connection.setPublicUrl(publicUrl);connection.setAutoDispatch(input.autoDispatch());
        if(input.clearToken()) connection.setEncryptedToken(null);
        else if(input.accessToken()!=null && !input.accessToken().isBlank()) {
            if(input.accessToken().chars().anyMatch(Character::isISOControl)) throw new BusinessRuleException("A credencial contém caracteres inválidos");
            connection.setEncryptedToken(cipher.encrypt(input.accessToken().trim()));
        }
        IntegrationConnection saved=connections.save(connection);
        events.recordAccount(AccountScope.userId(),"INTEGRATION_CONFIGURED","INFO","Integração configurada",IntegrationCatalog.find(slug).title(),IntegrationCatalog.find(slug).title(),saved.getId(),"CONFIGURED");
        return ConnectionView.from(saved);
    }
    @Transactional
    public void delete(String slug) {
        IntegrationConnection connection=connections.findByOwnerIdAndSlug(AccountScope.userId(),slug).orElseThrow(() -> new ResourceNotFoundException("Integração",slug));
        events.recordAccount(AccountScope.userId(),"INTEGRATION_REMOVED","INFO","Integração desconectada",IntegrationCatalog.find(slug).title(),IntegrationCatalog.find(slug).title(),connection.getId(),"REMOVED");
        connections.delete(connection);
    }
    public record ConnectionView(UUID systemId,String publicUrl,String actionPath,boolean credentialConfigured,boolean autoDispatch,String baseUrl,String healthEndpoint,int timeoutMs) {
        static ConnectionView from(IntegrationConnection connection){return new ConnectionView(connection.getSystemId(),connection.getPublicUrl(),connection.getActionPath(),connection.getEncryptedToken()!=null,connection.isAutoDispatch(),connection.getBaseUrl(),connection.getHealthEndpoint(),connection.getTimeoutMs());}
    }
}
