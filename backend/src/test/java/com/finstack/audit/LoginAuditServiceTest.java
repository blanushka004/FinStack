package com.finstack.audit;

import com.finstack.audit.dto.LoginAuditResponse;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAuditServiceTest {
    @Mock LoginAuditRepository repository;
    @Test void recordsMinimalSuccessfulAndFailedEventsWithoutCredentials() {
        LoginAuditService service = new LoginAuditService(repository);
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        service.record(user, "ada@example.com", true, "127.0.0.1", "test-agent", null);
        service.record(null, "unknown@example.com", false, "127.0.0.1", "test-agent", "INVALID_CREDENTIALS");
        ArgumentCaptor<LoginAuditEvent> events = ArgumentCaptor.forClass(LoginAuditEvent.class); verify(repository, org.mockito.Mockito.times(2)).save(events.capture());
        assertThat(events.getAllValues()).extracting(LoginAuditEvent::getEventType).containsExactly(LoginEventType.LOGIN_SUCCESS, LoginEventType.LOGIN_FAILURE);
        assertThat(events.getAllValues().getFirst().getUser()).isSameAs(user);
        assertThat(LoginAuditResponse.from(events.getAllValues().getFirst()).toString()).doesNotContain("hash", "password", "token", "jwt");
    }
}
