package com.finstack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.finstack.auth.EmailVerificationProperties;
import com.finstack.auth.PasswordResetProperties;
import com.finstack.event.KafkaProperties;

@SpringBootApplication
@EnableConfigurationProperties({EmailVerificationProperties.class, PasswordResetProperties.class, KafkaProperties.class})
public class FinstackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinstackApplication.class, args);
    }
}
