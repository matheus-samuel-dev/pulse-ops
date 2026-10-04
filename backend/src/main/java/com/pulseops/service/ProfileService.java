package com.pulseops.service;

import com.pulseops.domain.user.User;
import com.pulseops.dto.auth.AuthResponse;
import com.pulseops.dto.user.ProfileRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ConflictException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    public ProfileService(UserRepository users, AuthService auth, PasswordEncoder passwords) {
        this.users = users; this.auth = auth; this.passwords = passwords;
    }
    @Transactional(readOnly = true)
    public UserResponse me(UUID id) {
        return UserResponse.from(users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário", id)));
    }
    @Transactional
    public AuthResponse update(UUID id, ProfileRequest request) {
        User user = locked(id);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(user.getEmail())) {
            if (request.currentPassword() == null || !passwords.matches(request.currentPassword(), user.getPassword())) {
                throw new BusinessRuleException("Confirme sua senha atual para alterar o e-mail");
            }
            users.findByEmailIgnoreCase(email).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
                throw new ConflictException("Já existe uma conta com este e-mail");
            });
            user.setSessionVersion(user.getSessionVersion() + 1);
            user.setEmail(email);
        }
        user.setName(request.name().trim());
        return auth.response(users.save(user));
    }
    @Transactional
    public void logout(UUID id) {
        User user = locked(id);
        user.setSessionVersion(user.getSessionVersion() + 1);
        users.save(user);
    }
    private User locked(UUID id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
    }
}
