package com.pulseops.security;

import com.pulseops.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PulseOpsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    public PulseOpsUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isDemonstration() || (environment != null && environment.acceptsProfiles(
                        org.springframework.core.env.Profiles.of("demo & !prod"))))
                .map(PulseOpsPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }
}
