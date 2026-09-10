package com.pulseops.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class DemoModeWriteProtectionFilterTest {

    @Mock SecurityErrorWriter errorWriter;

    @Test
    void shouldBlockApiMutationInReadOnlyDemo() throws Exception {
        DemoModeWriteProtectionFilter filter = new DemoModeWriteProtectionFilter(
                new DemoModeProperties(true), errorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/systems");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "Forbidden", "O ambiente demonstrativo é somente leitura.");
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldAllowLoginAndReadsInReadOnlyDemo() throws Exception {
        DemoModeWriteProtectionFilter filter = new DemoModeWriteProtectionFilter(
                new DemoModeProperties(true), errorWriter);
        MockFilterChain loginChain = new MockFilterChain();
        MockFilterChain readChain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"),
                new MockHttpServletResponse(), loginChain);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/dashboard/summary"),
                new MockHttpServletResponse(), readChain);

        verify(errorWriter, never()).write(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.assertj.core.api.Assertions.assertThat(loginChain.getRequest()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(readChain.getRequest()).isNotNull();
    }

    @Test
    void shouldAllowMutationsOutsideDemoMode() throws Exception {
        DemoModeWriteProtectionFilter filter = new DemoModeWriteProtectionFilter(
                new DemoModeProperties(false), errorWriter);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("DELETE", "/api/systems/id"),
                new MockHttpServletResponse(), chain);

        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNotNull();
    }
}
