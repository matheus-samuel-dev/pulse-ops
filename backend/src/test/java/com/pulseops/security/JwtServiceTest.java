package com.pulseops.security;

import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    @Mock
    private UserDetails userDetails;

    private JwtProperties properties;
    private JwtService jwtService;
    private PulseOpsPrincipal principal;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, 3600, "pulseops-api");
        jwtService = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Ada Lovelace");
        user.setEmail("ada@pulseops.io");
        user.setPassword("encoded-password");
        user.setRole(UserRole.ADMIN);
        principal = PulseOpsPrincipal.from(user);
    }

    @Test
    void shouldGenerateSignedTokenWithIdentityRoleIssuerAndConfiguredExpiration() {
        String token = jwtService.generateToken(principal);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("ada@pulseops.io");
        assertThat(claims.getIssuer()).isEqualTo("pulseops-api");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("name", String.class)).isEqualTo("Ada Lovelace");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(jwtService.extractUsername(token)).isEqualTo("ada@pulseops.io");
        assertThat(jwtService.extractExpiration(token)).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void shouldUseInjectedUtcSystemClock() {
        Instant before = Instant.now();
        JwtService productionService = new JwtService(properties, Clock.systemUTC());

        Instant expiration = productionService.extractExpiration(productionService.generateToken(principal));

        assertThat(expiration)
                .isAfterOrEqualTo(before.plusSeconds(3598))
                .isBeforeOrEqualTo(Instant.now().plusSeconds(3601));
    }

    @Test
    void shouldValidateSignedUnexpiredTokenForMatchingUser() {
        String token = jwtService.generateToken(principal);
        when(userDetails.getUsername()).thenReturn("ada@pulseops.io");

        boolean valid = jwtService.isValid(token, userDetails);

        assertThat(valid).isTrue();
        verify(userDetails).getUsername();
    }

    @Test
    void shouldRejectTokenWhenUsernameDoesNotMatch() {
        String token = jwtService.generateToken(principal);
        when(userDetails.getUsername()).thenReturn("other@pulseops.io");

        assertThat(jwtService.isValid(token, userDetails)).isFalse();
    }

    @Test
    void shouldRejectTokenExpiredAccordingToApplicationClock() {
        String token = jwtService.generateToken(principal);
        JwtService futureService = new JwtService(
                properties,
                Clock.fixed(NOW.plusSeconds(7200), ZoneOffset.UTC)
        );
        when(userDetails.getUsername()).thenReturn("ada@pulseops.io");

        assertThat(futureService.isValid(token, userDetails)).isFalse();
    }

    @Test
    void shouldRejectMalformedTokenWithoutConsultingUserDetails() {
        assertThat(jwtService.isValid("not-a-jwt", userDetails)).isFalse();

        verify(userDetails, never()).getUsername();
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() {
        JwtProperties foreignProperties = new JwtProperties(
                "abcdef0123456789abcdef0123456789", 3600, "other-issuer");
        JwtService foreignService = new JwtService(foreignProperties, Clock.fixed(NOW, ZoneOffset.UTC));
        String foreignToken = foreignService.generateToken(principal);

        assertThat(jwtService.isValid(foreignToken, userDetails)).isFalse();
        verify(userDetails, never()).getUsername();
    }

    @Test
    void shouldExposeParsingFailureWhenCallerExtractsFromInvalidToken() {
        assertThatThrownBy(() -> jwtService.extractUsername("invalid-token"))
                .isInstanceOf(RuntimeException.class);
    }
}
