package com.pulseops.service;

import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.user.CreateUserRequest;
import com.pulseops.dto.user.UpdateUserRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.exception.ConflictException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void shouldNormalizeEmailTrimNameEncodePasswordAndPersistUser() {
            when(userRepository.existsByEmailIgnoreCase("dev@pulseops.io")).thenReturn(false);
            when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-hash");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            User result = userService.create(
                    "  Ada Lovelace  ", "  Dev@PulseOps.IO  ", "strong-password", UserRole.DEVELOPER);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(result).isSameAs(saved);
            assertThat(saved.getName()).isEqualTo("Ada Lovelace");
            assertThat(saved.getEmail()).isEqualTo("dev@pulseops.io");
            assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
            assertThat(saved.getRole()).isEqualTo(UserRole.DEVELOPER);
            verify(passwordEncoder).encode("strong-password");
        }

        @Test
        void shouldReturnSafeResponseWhenCreatingFromRequest() {
            when(userRepository.existsByEmailIgnoreCase("viewer@pulseops.io")).thenReturn(false);
            when(passwordEncoder.encode("viewer-pass")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            CreateUserRequest request = new CreateUserRequest(
                    "Viewer", "viewer@pulseops.io", "viewer-pass", UserRole.VIEWER);

            UserResponse response = userService.create(request);

            assertThat(response.id()).isNotNull();
            assertThat(response.name()).isEqualTo("Viewer");
            assertThat(response.email()).isEqualTo("viewer@pulseops.io");
            assertThat(response.role()).isEqualTo(UserRole.VIEWER);
        }

        @Test
        void shouldRejectDuplicateEmailWithoutEncodingOrSaving() {
            when(userRepository.existsByEmailIgnoreCase("admin@pulseops.io")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(
                    "Admin", " ADMIN@PulseOps.io ", "password", UserRole.ADMIN))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("e-mail");

            verifyNoInteractions(passwordEncoder);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updates")
    class Updates {

        @Test
        void shouldUpdateNameEmailAndRoleWhenEmailBelongsToSameUser() {
            UUID userId = UUID.randomUUID();
            User user = user(userId, "Old Name", "old@pulseops.io", UserRole.VIEWER);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.findByEmailIgnoreCase("new@pulseops.io")).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            UserResponse response = userService.update(
                    userId,
                    new UpdateUserRequest("  New Name  ", " NEW@PulseOps.io ", UserRole.DEVELOPER)
            );

            assertThat(response.name()).isEqualTo("New Name");
            assertThat(response.email()).isEqualTo("new@pulseops.io");
            assertThat(response.role()).isEqualTo(UserRole.DEVELOPER);
            verify(userRepository).save(user);
        }

        @Test
        void shouldRejectEmailOwnedByAnotherUserWithoutMutatingOrSaving() {
            UUID userId = UUID.randomUUID();
            User user = user(userId, "Current", "current@pulseops.io", UserRole.VIEWER);
            User owner = user(UUID.randomUUID(), "Owner", "taken@pulseops.io", UserRole.ADMIN);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.findByEmailIgnoreCase("taken@pulseops.io")).thenReturn(Optional.of(owner));

            assertThatThrownBy(() -> userService.update(
                    userId,
                    new UpdateUserRequest("Changed", "taken@pulseops.io", UserRole.ADMIN)))
                    .isInstanceOf(ConflictException.class);

            assertThat(user.getName()).isEqualTo("Current");
            assertThat(user.getEmail()).isEqualTo("current@pulseops.io");
            assertThat(user.getRole()).isEqualTo(UserRole.VIEWER);
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void shouldReturnUsersInRepositoryOrder() {
        User ada = user(UUID.randomUUID(), "Ada", "ada@pulseops.io", UserRole.ADMIN);
        User grace = user(UUID.randomUUID(), "Grace", "grace@pulseops.io", UserRole.DEVELOPER);
        when(userRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ada, grace));

        List<UserResponse> result = userService.findAll();

        assertThat(result).extracting(UserResponse::name).containsExactly("Ada", "Grace");
        verify(userRepository).findAllByOrderByNameAsc();
    }

    @Test
    void shouldFindAndDeleteExistingUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "Ada", "ada@pulseops.io", UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(userService.findById(userId).id()).isEqualTo(userId);
        userService.delete(userId);

        verify(userRepository).delete(user);
    }

    @Test
    void shouldThrowResourceNotFoundForUnknownUserAndNeverDelete() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
        assertThatThrownBy(() -> userService.delete(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository, never()).delete(any());
    }

    private User user(UUID id, String name, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }
}
