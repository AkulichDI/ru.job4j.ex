package ru.job4j.ex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class CountTest {
    @Test
    public void whenException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> {
            Count.add(10, 2);
        });
        assertThat(exception.getMessage()).isEqualTo("Start should be less than finish.");
    }
    @Test
    public void whenException1() {
        int start = 0;
        int finish = 3;
        int expected = 3;
        int result = Count.add(start, finish);
        assertThat(result).isEqualTo(expected);
    }

}
class FactorialTest {
    @Test
    public void whenException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                   Factorial.calc(-1);
                });
        assertThat(exception.getMessage()).isEqualTo("Number could not be less than 0");
    }

}

class PasswordValidatorTest {
    @Test
    void whenPasswordIsValid() {
        String password = "Ln2$mrTY12";
        String expected = "Ln2$mrTY12";
        String result = PasswordValidator.validate(password);
        assertThat(result).isEqualTo(expected);
    }
    @Test
    void whenPasswordIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,

        () -> PasswordValidator.validate(null)
        );
        String expected = "Password can't be null";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordLengthLess8() {
        String password = "Ln2$mrT";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should be length [8, 32]";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordLengthMore32() {
        String password = "Ln2$mrTY3245nMdsdfdfPPPg$#dg124531";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,

        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should be length [8, 32]";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordNotContainUpperCaseLetter() {
        String password = "ln2$mrty12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should contain at least one uppercase letter";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordNotContainLowerCaseLetter() {
        String password = "LN2$MRTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should contain at least one lowercase letter";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordNotContainFigure() {
        String password = "LnI$mrTYUo";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should contain at least one figure";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordNotContainSpecialSymbol() {
        String password = "Ln2pmrTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password should contain at least one special symbol";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordContainSubstringQWERTY() {
        String password = "Ln2$mrQWerTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password shouldn't contain substrings: qwerty, 12345, password, admin, user";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordContainSubstring12345() {
        String password = "Ln2$mrTY12345";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password shouldn't contain substrings: qwerty, 12345, password, admin, user";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordContainSubstringPassword() {
        String password = "LnPaSsWoRd2$mrTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PasswordValidator.validate(password)
        );
        String expected = "Password shouldn't contain substrings: qwerty, 12345, password, admin, user";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordContainSubstringAdmin() {
        String password = "Ln2$aDmiNrTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password shouldn't contain substrings: qwerty, 12345, password, admin, user";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
    @Test
    void whenPasswordContainSubstringUser() {
        String password = "Ln2$mUSerTY12";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
        () -> PasswordValidator.validate(password)
        );
        String expected = "Password shouldn't contain substrings: qwerty, 12345, password, admin, user";
        assertThat(exception.getMessage()).isEqualTo(expected);
    }
}