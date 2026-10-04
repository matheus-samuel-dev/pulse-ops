package com.pulseops.service.integration;
import com.pulseops.dto.report.OperationalEventResponse;
import com.pulseops.repository.OperationalEventRepository;
import com.pulseops.service.report.OperationalReportService;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class IntegrationActivityService {
    private final OperationalEventRepository events;private final Clock clock;
    public IntegrationActivityService(OperationalEventRepository events,Clock clock){this.events=events;this.clock=clock;}
    @Transactional(readOnly=true) public List<OperationalEventResponse> events(String slug){
        var now=OffsetDateTime.now(clock);String source=slug==null?null:IntegrationCatalog.find(slug).title();
        return events.integrationActivity(source,now.minusDays(30),now,PageRequest.of(0,12)).stream().map(OperationalReportService::event).toList();
    }
}
