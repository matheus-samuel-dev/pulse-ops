package com.pulseops.security;

import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("REST security handlers")
class RestSecurityHandlersTest {

    @Mock
    private SecurityErrorWriter errorWriter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private RestAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        authenticationEntryPoint = new RestAuthenticationEntryPoint(errorWriter);
        accessDeniedHandler = new RestAccessDeniedHandler(errorWriter);
    }

    @Test
    void shouldDelegateUnauthenticatedRequestAsStandard401Response() throws Exception {
        BadCredentialsException exception = new BadCredentialsException("expired credentials");

        authenticationEntryPoint.commence(request, response, exception);

        verify(errorWriter).write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Autenticação necessária"
        );
    }

    @Test
    void shouldDelegateUnauthorizedRequestAsStandard403Response() throws Exception {
        AccessDeniedException exception = new AccessDeniedException("missing ADMIN role");

        accessDeniedHandler.handle(request, response, exception);

        verify(errorWriter).write(
                request,
                response,
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Acesso negado para este recurso"
        );
    }
}
