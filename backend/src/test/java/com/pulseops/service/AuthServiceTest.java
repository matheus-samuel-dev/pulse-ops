package com.pulseops.service;

import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.auth.AuthResponse;
import com.pulseops.dto.auth.LoginRequest;
import com.pulseops.dto.auth.RegisterRequest;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.UserRepository;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private AuthService authService;

    @Test
    void shouldAuthenticateNormalizedEmailAndReturnBearerToken() {
        User user = user("dev@pulseops.io", UserRole.DEVELOPER);
        Instant expiration = Instant.parse("2026-08-27T16:00:00Z");
        when(userRepository.findByEmailIgnoreCase("dev@pulseops.io")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(PulseOpsPrincipal.class))).thenReturn("signed.jwt.token");
        when(jwtService.extractExpiration("signed.jwt.token")).thenReturn(expiration);

        AuthResponse response = authService.login(new LoginRequest("  DEV@PulseOps.IO  ", "secret-123"));

        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class)
                .extracting(Authentication::getPrincipal, Authentication::getCredentials)
                .containsExactly("dev@pulseops.io", "secret-123");

        ArgumentCaptor<PulseOpsPrincipal> principalCaptor = ArgumentCaptor.forClass(PulseOpsPrincipal.class);
        verify(jwtService).generateToken(principalCaptor.capture());
        assertThat(principalCaptor.getValue().id()).isEqualTo(user.getId());
        assertThat(principalCaptor.getValue().role()).isEqualTo("DEVELOPER");
        assertThat(response.token()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiration);
        assertThat(response.user().email()).isEqualTo("dev@pulseops.io");
    }

    @Test
    void shouldPropagateAuthenticationFailureWithoutReadingUserOrGeneratingToken() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@pulseops.io", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad credentials");

        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void shouldFailWhenAuthenticatedPrincipalNoLongerExists() {
        when(userRepository.findByEmailIgnoreCase("missing@pulseops.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("MISSING@pulseops.io", "secret-123")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing@pulseops.io");

        verify(authenticationManager).authenticate(any(Authentication.class));
        verify(jwtService, never()).generateToken(any());
        verify(jwtService, never()).extractExpiration(any());
    }

    @Test
    void shouldRegisterPublicAccountsAsDeveloperAndIssueToken() {
        User user = user("new.user@pulseops.io", UserRole.DEVELOPER);
        Instant expiration = Instant.parse("2026-08-27T16:00:00Z");
        when(userService.create("New User", " NEW.USER@pulseops.io ", "strong-pass", UserRole.DEVELOPER))
                .thenReturn(user);
        when(jwtService.generateToken(any(PulseOpsPrincipal.class))).thenReturn("registration-token");
        when(jwtService.extractExpiration("registration-token")).thenReturn(expiration);

        AuthResponse response = authService.register(
                new RegisterRequest("New User", " NEW.USER@pulseops.io ", "strong-pass"));

        verify(userService).create("New User", " NEW.USER@pulseops.io ", "strong-pass", UserRole.DEVELOPER);
        ArgumentCaptor<PulseOpsPrincipal> principalCaptor = ArgumentCaptor.forClass(PulseOpsPrincipal.class);
        verify(jwtService).generateToken(principalCaptor.capture());
        assertThat(principalCaptor.getValue().role()).isEqualTo("DEVELOPER");
        assertThat(response.token()).isEqualTo("registration-token");
        assertThat(response.expiresAt()).isEqualTo(expiration);
        assertThat(response.user().role()).isEqualTo(UserRole.DEVELOPER);
        verifyNoInteractions(authenticationManager, userRepository);
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("PulseOps User");
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setRole(role);
        return user;
    }
}
