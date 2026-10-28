package dev.wasdevv.minierp.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneTest {

    @Test
    void normalizesCommonWaysOfTypingANumber() {
        assertThat(Phone.normalize("(11) 98765-4321")).isEqualTo("11987654321");
        assertThat(Phone.normalize("+55 11 98765-4321")).isEqualTo("11987654321");
        assertThat(Phone.normalize("011 98765-4321")).isEqualTo("11987654321");
        assertThat(Phone.normalize("(11) 3456-7890")).isEqualTo("1134567890");
        assertThat(Phone.normalize(" () ")).isNull();
        assertThat(Phone.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11987654321", "1134567890", "21999998888"})
    void acceptsMobileAndLandline(String digits) {
        assertThat(Phone.isValid(digits)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "119876543210", "11887654321", "0198765432", "1198765432a", ""})
    void rejectsWrongLengthMobileWithoutNineAndAreaCodeZero(String digits) {
        assertThat(Phone.isValid(digits)).isFalse();
    }

    @Test
    void formatsForDisplay() {
        assertThat(Phone.format("11987654321")).isEqualTo("(11) 98765-4321");
        assertThat(Phone.format("1134567890")).isEqualTo("(11) 3456-7890");
        assertThat(Phone.format(null)).isNull();
        assertThat(Phone.format("123")).isEqualTo("123");
    }
}
