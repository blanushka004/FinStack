package com.finstack.auth;

import com.finstack.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.net.URI;

@Service
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final ObjectProvider<JavaMailSender> mailSender;
    private final EmailVerificationProperties properties;
    private final String from;
    private final PasswordResetProperties resetProperties;
    private final Environment environment;
    private final String mailHost;
    public EmailDeliveryService(ObjectProvider<JavaMailSender> mailSender, EmailVerificationProperties properties,
            PasswordResetProperties resetProperties, Environment environment, @Value("${spring.mail.from:}") String from,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender=mailSender; this.properties=properties; this.resetProperties=resetProperties; this.environment=environment; this.from=from; this.mailHost=mailHost;
    }
    public void sendVerification(User user, String rawToken) {
        String link = properties.frontendBaseUrl().replaceAll("/$", "") + "/verify-email?token=" + rawToken;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null && !from.isBlank()) {
            SimpleMailMessage message = new SimpleMailMessage(); message.setTo(user.getEmail()); message.setFrom(from); message.setSubject("Verify your FinStack email"); message.setText("Verify your FinStack email: " + link); sender.send(message); return;
        }
        if (properties.developmentLogLink()) log.info("Email verification link for {}: {}", user.getEmail(), link);
        else log.warn("Email verification delivery is not configured for user {}", user.getId());
    }
    public void sendPasswordReset(User user, String rawToken) {
        String base = properties.frontendBaseUrl().replaceAll("/$", "");
        String link = base + "/reset-password?token=" + rawToken;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null && !from.isBlank() && !mailHost.isBlank()) {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(user.getEmail()); mail.setFrom(from); mail.setSubject("Reset your FinStack password");
            mail.setText("Use this one-time link to reset your FinStack password: " + link + "\nIf you did not request this, ignore this message.");
            try { sender.send(mail); } catch (RuntimeException exception) { log.warn("Password reset email delivery failed for user {}", user.getId()); }
            return;
        }
        URI frontend = URI.create(base);
        boolean loopback = "localhost".equalsIgnoreCase(frontend.getHost()) || "127.0.0.1".equals(frontend.getHost());
        String bindAddress = environment.getProperty("server.address", "0.0.0.0");
        boolean localServer = "localhost".equalsIgnoreCase(bindAddress) || "127.0.0.1".equals(bindAddress);
        if (resetProperties.developmentLogLink() && environment.acceptsProfiles(Profiles.of("dev")) && loopback && localServer) {
            log.info("DEV ONLY password reset link for user {}: {}", user.getId(), link);
        } else {
            log.warn("Password reset delivery is not configured for user {}", user.getId());
        }
    }
}
