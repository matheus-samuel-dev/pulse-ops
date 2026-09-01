package com.pulseops.service;

import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.auth.AuthResponse;
import com.pulseops.dto.auth.LoginRequest;
import com.pulseops.dto.auth.RegisterRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.UserRepository;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsPrincipal;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            UserService userService,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return response(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = userService.create(request.name(), request.email(), request.password(), UserRole.VIEWER);
        return response(user);
    }

    private AuthResponse response(User user) {
        String token = jwtService.generateToken(PulseOpsPrincipal.from(user));
        return new AuthResponse(token, "Bearer", jwtService.extractExpiration(token), UserResponse.from(user));
    }
}
