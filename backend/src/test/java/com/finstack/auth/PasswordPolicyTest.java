package com.finstack.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {
    @Test void acceptsACompletePassword() { assertThat(PasswordPolicy.isValid("Correct-Horse1!")).isTrue(); }
    @Test void rejectsPasswordsMissingRequiredCharacterClasses() { assertThat(PasswordPolicy.isValid("alllowercase1!")).isFalse(); assertThat(PasswordPolicy.isValid("NoNumber!")).isFalse(); assertThat(PasswordPolicy.isValid("NoSpecial1")).isFalse(); }
}
