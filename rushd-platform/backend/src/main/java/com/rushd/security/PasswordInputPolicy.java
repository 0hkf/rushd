package com.rushd.security;

import java.nio.charset.StandardCharsets;

/** BCrypt's input limit is measured in UTF-8 bytes, not Java characters. No trimming or normalization. */
public final class PasswordInputPolicy {
    public static final int MAX_BYTES = 72;
    private PasswordInputPolicy() {}

    public static boolean isSupportedLength(CharSequence value) {
        // Avoid allocating an unbounded UTF-8 array for a clearly oversized input.
        return value != null && value.length() <= MAX_BYTES
                && value.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
