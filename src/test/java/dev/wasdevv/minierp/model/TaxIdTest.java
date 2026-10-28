package dev.wasdevv.minierp.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TaxIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "11222333000181", "45723174000110"})
    void acceptsValidCpfAndCnpj(String digits) {
        assertThat(TaxId.isValid(digits)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "52998224735", "11222333000182", "11111111111", "00000000000000",
            "1234567890", "123456789012", "5299822472a", ""})
    void rejectsWrongCheckDigitsRepeatedDigitsAndWrongLength(String digits) {
        assertThat(TaxId.isValid(digits)).isFalse();
    }

    @Test
    void normalizeKeepsDigitsOnly() {
        assertThat(TaxId.normalize("529.982.247-25")).isEqualTo("52998224725");
        assertThat(TaxId.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(TaxId.normalize(" .-/ ")).isNull();
        assertThat(TaxId.normalize(null)).isNull();
    }

    @Test
    void formatsForDisplay() {
        assertThat(TaxId.format("52998224725")).isEqualTo("529.982.247-25");
        assertThat(TaxId.format("11222333000181")).isEqualTo("11.222.333/0001-81");
    }

    @Test
    void masksFirstBlockAndCheckDigits() {
        assertThat(TaxId.mask("52998224725")).isEqualTo("***.982.247-**");
        assertThat(TaxId.mask("11222333000181")).isEqualTo("**.222.333/0001-**");
        assertThat(TaxId.mask(null)).isNull();
    }
}
