package com.pulseops.service;

import com.pulseops.domain.event.OperationalEvent;
import com.pulseops.domain.notification.Notification;
import com.pulseops.domain.notification.NotificationType;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.repository.NotificationRepository;
import com.pulseops.repository.OperationalEventRepository;
import com.pulseops.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRecorder {
    private final OperationalEventRepository events;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    public EventRecorder(OperationalEventRepository events, NotificationRepository notifications,
            UserRepository users, Clock clock, ApplicationEventPublisher publisher) {
        this.events = events; this.notifications = notifications; this.users = users; this.clock = clock; this.publisher = publisher;
    }
    @Transactional
    public OperationalEvent record(MonitoredSystem system, String type, String severity, String title, String description, String source) {
        return recordResource(system,type,severity,title,description,source,system.getId(),system.getStatus().name());
    }
    @Transactional
    public OperationalEvent recordResource(MonitoredSystem system,String type,String severity,String title,String description,String source,java.util.UUID resourceId,String status) {
        return recordResourceInEnvironment(system,system.getEnvironment(),type,severity,title,description,source,resourceId,status);
    }
    @Transactional
    public OperationalEvent recordResourceInEnvironment(MonitoredSystem system,com.pulseops.domain.system.Environment environment,String type,String severity,String title,String description,String source,java.util.UUID resourceId,String status) {
        OperationalEvent event = new OperationalEvent();
        event.setOwnerId(system.getOwnerId()); event.setSystemId(system.getId());
        event.setSystemName(system.getName()); event.setEnvironment(environment.name());
        event.setType(type); event.setSeverity(severity); event.setTitle(title);
        event.setDescription(description == null ? null : description.substring(0, Math.min(description.length(), 2000)));
        event.setSource(source); event.setOccurredAt(OffsetDateTime.now(clock));
        event.setResourceId(resourceId); event.setStatus(status);
        event = events.save(event);
        if (system.getOwnerId() != null && (type.equals("SYSTEM_DOWN") || type.equals("SYSTEM_CONFIGURATION_REQUIRED") || type.equals("SYSTEM_RECOVERED") || type.equals("INCIDENT_OPENED") || type.equals("DEPLOYMENT_FAILED") || type.equals("INTEGRATION_FAILED"))) {
            Notification notice = new Notification(); notice.setUser(users.getReferenceById(system.getOwnerId()));
            notice.setTitle(title); notice.setMessage(description == null || description.isBlank() ? title : event.getDescription());
            notice.setType(type.startsWith("INCIDENT") ? NotificationType.INCIDENT : type.startsWith("DEPLOYMENT") ? NotificationType.DEPLOYMENT : type.equals("SYSTEM_DOWN") || type.equals("SYSTEM_CONFIGURATION_REQUIRED") || type.startsWith("INTEGRATION") ? NotificationType.ERROR : NotificationType.SUCCESS);
            notice.setEventId(event.getId()); notice.setSystemId(event.getSystemId()); notice.setResourceId(resourceId);
            notifications.save(notice);
        }
        publisher.publishEvent(new Recorded(event.getId(), event.getSystemId(), event.getOwnerId(), event.getType()));
        return event;
    }
    @Transactional
    public OperationalEvent recordAccount(java.util.UUID owner,String type,String severity,String title,String description,String source,java.util.UUID resourceId,String status) {
        OperationalEvent event=new OperationalEvent();event.setOwnerId(owner);event.setSystemName("Conta pessoal");
        event.setType(type);event.setSeverity(severity);event.setTitle(title);event.setDescription(description);event.setSource(source);
        event.setResourceId(resourceId);event.setStatus(status);event.setOccurredAt(OffsetDateTime.now(clock));
        event=events.save(event);
        if(owner!=null && type.startsWith("INTEGRATION") && severity.equals("ERROR")) {
            Notification notice=new Notification();notice.setUser(users.getReferenceById(owner));notice.setTitle(title);notice.setMessage(description==null?title:description);notice.setType(NotificationType.ERROR);notice.setEventId(event.getId());notice.setResourceId(resourceId);notifications.save(notice);
        }
        return event;
    }
    public record Recorded(java.util.UUID id, java.util.UUID systemId, java.util.UUID ownerId, String type) { }
}
