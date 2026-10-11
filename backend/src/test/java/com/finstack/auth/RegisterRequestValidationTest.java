package com.finstack.auth;

import com.finstack.auth.dto.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {
    @Test void rejectsMalformedEmailAndWeakPasswordAtTheRequestBoundary() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new RegisterRequest("Ada", "Lovelace", "not-an-email", "weakpass"));
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString()).contains("email", "password");
    }
}
