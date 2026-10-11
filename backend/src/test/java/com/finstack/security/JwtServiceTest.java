package com.finstack.security;

import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";

    @Test
    void generatesAValidTokenWhoseSubjectIsTheUserEmail() {
        JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 3_600_000));
        User user = activeUser();

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractSubject(token)).contains("ada@example.com");
    }

    @Test
    void rejectsExpiredTokens() throws InterruptedException {
        JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 1));
        String token = jwtService.generateAccessToken(activeUser());

        Thread.sleep(25);

        assertThat(jwtService.isTokenValid(token)).isFalse();
        assertThat(jwtService.extractSubject(token)).isEmpty();
    }

    @Test
    void rejectsMalformedAndIncorrectlySignedTokens() {
        JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 3_600_000));
        JwtService differentKeyService = new JwtService(
                new JwtProperties("a-different-test-secret-that-is-at-least-32-bytes", 3_600_000)
        );
        String token = jwtService.generateAccessToken(activeUser());

        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
        assertThat(jwtService.extractSubject("not-a-jwt")).isEmpty();
        assertThat(differentKeyService.isTokenValid(token)).isFalse();
    }

    @Test
    void passwordResetRevokesEarlierTokensWithoutChangingOtherUsers() {
        JwtService service = new JwtService(new JwtProperties(TEST_SECRET, 3_600_000));
        User user = activeUser();
        String oldToken = service.generateAccessToken(user);
        assertThat(service.matchesTokenVersion(oldToken, user.getTokenVersion())).isTrue();
        user.resetPassword("new-bcrypt-hash");
        assertThat(service.matchesTokenVersion(oldToken, user.getTokenVersion())).isFalse();
        assertThat(service.matchesTokenVersion(service.generateAccessToken(user), user.getTokenVersion())).isTrue();
        assertThat(service.matchesTokenVersion(oldToken, 0)).isTrue();
    }

    private User activeUser() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "password-hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        return user;
    }
}
