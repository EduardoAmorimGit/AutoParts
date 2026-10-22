package com.autoparts_maven.autoparts_maven.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlateNormalizerTest {

    @Test
    void normalizesTrimUppercaseAndRemovesHyphenAndSpaces() {
        assertThat(PlateNormalizer.normalize(" abc-1234 ")).isEqualTo("ABC1234");
        assertThat(PlateNormalizer.normalize("abc1d23")).isEqualTo("ABC1D23");
        assertThat(PlateNormalizer.normalize("ab c1d2 3")).isEqualTo("ABC1D23");
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(PlateNormalizer.normalize(null)).isNull();
    }

    @Test
    void acceptsMercosulAndLegacyFormats() {
        assertThat(PlateNormalizer.isValid("ABC1D23")).isTrue();
        assertThat(PlateNormalizer.isValid("ABC1234")).isTrue();
    }

    @Test
    void rejectsInvalidFormats() {
        assertThat(PlateNormalizer.isValid(null)).isFalse();
        assertThat(PlateNormalizer.isValid("")).isFalse();
        assertThat(PlateNormalizer.isValid("AB1234")).isFalse();
        assertThat(PlateNormalizer.isValid("ABCD123")).isFalse();
        assertThat(PlateNormalizer.isValid("ABC12345")).isFalse();
        assertThat(PlateNormalizer.isValid("1234ABC")).isFalse();
    }
}
