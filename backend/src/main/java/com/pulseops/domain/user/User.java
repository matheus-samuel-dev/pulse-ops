package com.pulseops.domain.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pulseops.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(
        name = "app_users",
        indexes = @Index(name = "idx_app_users_role", columnList = "role"),
        uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email")
)
public class User extends AuditableEntity {
    @Column(nullable = false)
    private boolean demonstration;
    public boolean isDemonstration() { return demonstration; }
    @Column(name = "session_version", nullable = false)
    private long sessionVersion;
    public long getSessionVersion() { return sessionVersion; }
    public void setSessionVersion(long value) { sessionVersion = value; }

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.VIEWER;

    public User() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
