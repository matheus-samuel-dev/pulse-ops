package com.pulseops.service;

import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.user.CreateUserRequest;
import com.pulseops.dto.user.UpdateUserRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.exception.ConflictException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User create(String name, String email, String rawPassword, UserRole role) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return UserResponse.from(create(request.name(), request.email(), request.password(), request.role()));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllByOrderByNameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public User findEntity(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.from(findEntity(id));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findEntity(id);
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Já existe um usuário com este e-mail");
                });
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setRole(request.role());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = findEntity(id);
        userRepository.delete(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
