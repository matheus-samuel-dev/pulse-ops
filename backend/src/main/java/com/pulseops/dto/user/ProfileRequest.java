package com.pulseops.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 72) String currentPassword
) { }
