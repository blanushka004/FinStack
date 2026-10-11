package com.finstack.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordResetDeliveryTest {
    private static final User USER = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);

    @Test void resetLinkIsNeverLoggedWithoutExplicitLocalDevConditions() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.address", "127.0.0.1");
        assertThat(logFor(environment, true)).doesNotContain("reset-password?token=");
        environment.setActiveProfiles("dev");
        environment.withProperty("server.address", "0.0.0.0");
        assertThat(logFor(environment, true)).doesNotContain("reset-password?token=");
        environment.withProperty("server.address", "127.0.0.1");
        assertThat(logFor(environment, false)).doesNotContain("reset-password?token=");
        assertThat(logFor(environment, true)).contains("reset-password?token=");
    }

    @Test void partialMailSettingsDoNotHideTheLocalDevelopmentLink() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.address", "127.0.0.1");
        environment.setActiveProfiles("dev");
        Logger logger = (Logger) LoggerFactory.getLogger(EmailDeliveryService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(events);
        try {
            @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
            JavaMailSender sender = mock(JavaMailSender.class);
            when(provider.getIfAvailable()).thenReturn(sender);
            EmailDeliveryService service = new EmailDeliveryService(provider,
                    new EmailVerificationProperties(30, "http://localhost:5173", false),
                    new PasswordResetProperties(30, true), environment, "sender@example.com", "");

            service.sendPasswordReset(USER, "test-token");

            verifyNoInteractions(sender);
            assertThat(events.list.stream().map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(text -> assertThat(text).contains("DEV ONLY password reset link"));
        } finally {
            logger.detachAppender(events);
            logger.setAdditive(additive);
            events.stop();
        }
    }

    @Test void verificationLinksAreNotLoggedOutsideExplicitLoopbackDevelopmentMode() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.address", "0.0.0.0");
        environment.setActiveProfiles("prod");
        assertThat(verificationLogFor(environment, "https://finstack-demo.vercel.app"))
                .doesNotContain("verify-email?token=");
        environment.setActiveProfiles("dev");
        assertThat(verificationLogFor(environment, "http://localhost:5173"))
                .doesNotContain("verify-email?token=");
        environment.withProperty("server.address", "127.0.0.1");
        assertThat(verificationLogFor(environment, "http://localhost:5173"))
                .contains("DEV ONLY email verification link");
    }

    private String verificationLogFor(MockEnvironment environment, String frontendUrl) {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailDeliveryService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(events);
        try {
            @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
            EmailDeliveryService service = new EmailDeliveryService(sender,
                    new EmailVerificationProperties(30, frontendUrl, true),
                    new PasswordResetProperties(30, false), environment, "", "");
            service.sendVerification(USER, "test-token");
            return events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        } finally {
            logger.detachAppender(events);
            logger.setAdditive(additive);
            events.stop();
        }
    }

    private String logFor(MockEnvironment environment, boolean enabled) {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailDeliveryService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(events);
        try {
            @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
            EmailDeliveryService service = new EmailDeliveryService(sender,
                    new EmailVerificationProperties(30, "http://localhost:5173", false),
                    new PasswordResetProperties(30, enabled), environment, "", "");
            service.sendPasswordReset(USER, "test-token");
            return events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        } finally {
            logger.detachAppender(events);
            logger.setAdditive(additive);
            events.stop();
        }
    }
}
