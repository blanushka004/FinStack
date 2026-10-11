package com.finstack.alert;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private UserRepository userRepository;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void marksOnlyTheCurrentUsersAlertAsRead() {
        Alert alert = new Alert(user, AlertType.BUDGET_WARNING, AlertSeverity.WARNING, "Warning", "Message", null);
        UUID alertId = UUID.randomUUID();
        ReflectionTestUtils.setField(alert, "id", alertId);
        when(alertRepository.findByIdAndUserId(alertId, user.getId())).thenReturn(Optional.of(alert));

        new AlertService(alertRepository, userRepository).markRead(alertId);

        assertThat(alert.isRead()).isTrue();
        verify(alertRepository).save(alert);
    }

    @Test
    void doesNotRevealAnotherUsersAlert() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findByIdAndUserId(alertId, user.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AlertService(alertRepository, userRepository).markRead(alertId))
                .isInstanceOf(AlertNotFoundException.class);
    }
}
