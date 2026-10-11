package com.finstack.security;

import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void mapsStoredRolesToGrantedAuthoritiesForAnActiveUser() {
        User user = new User("Ada", "Lovelace", "admin@example.com", "$2a$10$hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        user.addRole(new Role(RoleName.ROLE_ADMIN));
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        UserDetails userDetails = service.loadUserByUsername(" ADMIN@EXAMPLE.COM ");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findByEmailIgnoreCase(emailCaptor.capture());
        assertThat(emailCaptor.getValue()).isEqualTo("admin@example.com");
        assertThat(userDetails.getUsername()).isEqualTo("admin@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }
}
