package com.pulseops.dto.user;

import com.pulseops.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email String email,
        @NotNull UserRole role
) {
}
