package com.rushd.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordInputPolicyTest {
    @Test void utf8LimitIncludesArabicEmojiAndMixedTextWithoutNormalization() {
        assertTrue(PasswordInputPolicy.isSupportedLength("a".repeat(72)));
        assertFalse(PasswordInputPolicy.isSupportedLength("a".repeat(73)));
        assertTrue(PasswordInputPolicy.isSupportedLength("ع".repeat(36)));
        assertFalse(PasswordInputPolicy.isSupportedLength("ع".repeat(36) + "a"));
        assertTrue(PasswordInputPolicy.isSupportedLength("😀".repeat(18)));
        assertFalse(PasswordInputPolicy.isSupportedLength("😀".repeat(18) + "a"));
        assertTrue(PasswordInputPolicy.isSupportedLength("a".repeat(70) + "ع"));
        assertFalse(PasswordInputPolicy.isSupportedLength("a".repeat(71) + "ع"));
        assertFalse(PasswordInputPolicy.isSupportedLength(null));
    }

    @Test void actualEncoderNeverHashesOrMatchesOversizedInputAndPreservesOrdinaryHashes() {
        var encoder = new BoundedBCryptPasswordEncoder();
        for (String password : new String[] {"Password123", "كلمة مرور صحيحة", "a".repeat(72), "ع".repeat(36)}) {
            String hash = encoder.encode(password);
            assertTrue(encoder.matches(password, hash));
            assertFalse(encoder.matches(password + "x", hash));
            assertFalse(encoder.matches("z" + password.substring(1), hash));
        }
        assertThrows(IllegalArgumentException.class, () -> encoder.encode("a".repeat(73)));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode("ع".repeat(37)));
        String legacyHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("a".repeat(72));
        assertFalse(encoder.matches("a".repeat(72) + "-old-suffix", legacyHash));
        assertFalse(encoder.matches(null, legacyHash));
    }
}
