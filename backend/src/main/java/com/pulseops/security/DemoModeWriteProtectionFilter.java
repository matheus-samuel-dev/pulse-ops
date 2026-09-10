package com.pulseops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoModeWriteProtectionFilter extends OncePerRequestFilter {

    private final DemoModeProperties properties;
    private final SecurityErrorWriter errorWriter;

    public DemoModeWriteProtectionFilter(DemoModeProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isBlockedWrite(request)) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN.value(),
                    "Forbidden", "O ambiente demonstrativo é somente leitura.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlockedWrite(HttpServletRequest request) {
        if (!properties.readOnly() || !request.getRequestURI().startsWith("/api/")) {
            return false;
        }
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
            return false;
        }
        return !(HttpMethod.POST.matches(method) && "/api/auth/login".equals(request.getRequestURI()));
    }
}
