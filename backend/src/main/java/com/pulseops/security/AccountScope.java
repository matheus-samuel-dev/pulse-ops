package com.pulseops.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Repository predicates apply ownership to every user-facing aggregate and lookup.
 * Background monitoring has no authentication; administrators explicitly manage legacy records.
 */
public final class AccountScope {
    private AccountScope() { }
    public static final String SYSTEM = "system.demonstration = false and (:#{T(com.pulseops.security.AccountScope).unrestricted()} = true or system.ownerId = :#{T(com.pulseops.security.AccountScope).userId()})";
    public static boolean unrestricted() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    public static UUID userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof PulseOpsPrincipal principal ? principal.id() : null;
    }
}
