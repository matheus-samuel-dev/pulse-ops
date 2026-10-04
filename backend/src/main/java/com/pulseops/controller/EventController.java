package com.pulseops.controller;

import com.pulseops.domain.event.OperationalEvent;
import com.pulseops.repository.OperationalEventRepository;
import com.pulseops.service.MonitoredSystemService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/events")
public class EventController {
    @org.springframework.beans.factory.annotation.Autowired private com.pulseops.service.OperationalReadModel readModel;
    private final OperationalEventRepository events;
    private final MonitoredSystemService systems;
    public EventController(OperationalEventRepository events, MonitoredSystemService systems) { this.events = events; this.systems = systems; }
    @GetMapping
    public Page<OperationalEvent> list(@RequestParam(required = false) UUID systemId,
            @RequestParam(required = false) @Size(max = 20) String severity,
            @RequestParam(defaultValue = "") @Size(max = 200) String query,
            @RequestParam(required=false) String period,
            @RequestParam(required=false) com.pulseops.domain.system.Environment environment,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        if (systemId != null) systems.findById(systemId);
        var window=period==null?null:readModel.window(period);
        return events.searchWindow(systemId, severity, query,environment==null?null:environment.name(),window==null?null:window.start(),window==null?null:window.end(), PageRequest.of(page, size));
    }
}
