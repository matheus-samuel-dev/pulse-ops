package com.pulseops.controller;

import com.pulseops.dto.auth.AuthResponse;
import com.pulseops.dto.user.ProfileRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.security.PulseOpsPrincipal;
import com.pulseops.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class ProfileController {
    private final ProfileService profile;
    public ProfileController(ProfileService profile) { this.profile = profile; }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal PulseOpsPrincipal user) { return profile.me(user.id()); }
    @PutMapping("/me")
    public AuthResponse update(@AuthenticationPrincipal PulseOpsPrincipal user, @Valid @RequestBody ProfileRequest request) {
        return profile.update(user.id(), request);
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal PulseOpsPrincipal user) { profile.logout(user.id()); }
}
