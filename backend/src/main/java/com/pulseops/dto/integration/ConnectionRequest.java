package com.pulseops.dto.integration;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ConnectionRequest(UUID systemId,@Size(max=2048) String publicUrl,
        @NotBlank @Size(max=512) String actionPath,@Size(max=8192) String accessToken,
        boolean clearToken,boolean autoDispatch,@Size(max=2048) String baseUrl,@Size(max=512) String healthEndpoint,@Min(100) @Max(60000) Integer timeoutMs) {
    public ConnectionRequest(UUID systemId,String publicUrl,String actionPath,String accessToken,boolean clearToken,boolean autoDispatch){this(systemId,publicUrl,actionPath,accessToken,clearToken,autoDispatch,null,null,null);}
}
