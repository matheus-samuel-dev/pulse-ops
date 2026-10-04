package com.pulseops.security;

import com.pulseops.domain.user.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record PulseOpsPrincipal(
        UUID id,
        String name,
        String email,
        String password,
        String role,
        long sessionVersion
) implements UserDetails {

    public PulseOpsPrincipal(UUID id, String name, String email, String password, String role) {
        this(id, name, email, password, role, 0);
    }

    public static PulseOpsPrincipal from(User user) {
        return new PulseOpsPrincipal(
                user.getId(), user.getName(), user.getEmail(), user.getPassword(), user.getRole().name(), user.getSessionVersion());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }
}
