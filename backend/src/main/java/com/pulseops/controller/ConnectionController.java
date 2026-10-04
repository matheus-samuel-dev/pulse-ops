package com.pulseops.controller;
import com.pulseops.domain.integration.IntegrationRun;
import com.pulseops.dto.integration.ConnectionRequest;
import com.pulseops.service.integration.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
@RequestMapping("/api/connections")
public class ConnectionController {
    private final ConnectionService connections;
    private final IntegrationActionService actions;
    public ConnectionController(ConnectionService connections,IntegrationActionService actions){this.connections=connections;this.actions=actions;}
    @GetMapping("/{slug}") public ConnectionService.ConnectionView get(@PathVariable String slug){return connections.get(slug);}
    @PutMapping("/{slug}") @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ConnectionService.ConnectionView save(@PathVariable String slug,@Valid @RequestBody ConnectionRequest request){return connections.save(slug,request);}
    @DeleteMapping("/{slug}") @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String slug){connections.delete(slug);}
    @PostMapping("/{slug}/actions") @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')") @ResponseStatus(HttpStatus.CREATED)
    public IntegrationRun request(@PathVariable String slug,@Valid @RequestBody ActionRequest request){return actions.request(slug,request.systemId(),request.authorizationConfirmed());}
    @GetMapping("/runs") public Page<IntegrationRun> list(@RequestParam(required=false) UUID systemId,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return actions.list(systemId,page,size);}
    @PostMapping("/runs/{id}/refresh") @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public IntegrationRun refresh(@PathVariable UUID id){return actions.refresh(id);}
    @PatchMapping("/runs/{id}") @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public IntegrationRun callback(@PathVariable UUID id,@Valid @RequestBody ExecutionUpdate request){return actions.callback(id,request.state(),request.externalId());}
    public record ActionRequest(@NotNull UUID systemId,boolean authorizationConfirmed){ }
    public record ExecutionUpdate(@NotBlank @Size(max=30) String state,@Size(max=120) String externalId){ }
}
