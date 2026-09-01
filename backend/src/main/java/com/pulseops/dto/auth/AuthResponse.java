package com.pulseops.dto.auth;

import com.pulseops.dto.user.UserResponse;
import java.time.Instant;

public record AuthResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
