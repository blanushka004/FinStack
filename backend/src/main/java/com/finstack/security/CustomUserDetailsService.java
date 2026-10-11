package com.finstack.security;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toUnmodifiableSet());

        return new VersionedUserDetails(user.getEmail(), user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE, authorities, user.getTokenVersion());
    }

    public static final class VersionedUserDetails extends org.springframework.security.core.userdetails.User {
        private final long tokenVersion;

        VersionedUserDetails(String email, String passwordHash, boolean enabled,
                             Set<GrantedAuthority> authorities, long tokenVersion) {
            super(email, passwordHash, enabled, true, true, true, authorities);
            this.tokenVersion = tokenVersion;
        }

        public long getTokenVersion() { return tokenVersion; }
    }
}
