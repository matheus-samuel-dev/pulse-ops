package com.pulseops.security;

import com.pulseops.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PulseOpsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public PulseOpsUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(PulseOpsPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }
}
