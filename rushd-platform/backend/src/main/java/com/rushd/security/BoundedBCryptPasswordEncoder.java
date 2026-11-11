package com.rushd.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Local fail-closed boundary; keeps existing BCrypt hashes and cost unchanged. */
public final class BoundedBCryptPasswordEncoder implements PasswordEncoder {
    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override public String encode(CharSequence rawPassword) {
        if (!PasswordInputPolicy.isSupportedLength(rawPassword)) {
            throw new IllegalArgumentException("Password exceeds the supported UTF-8 byte length");
        }
        return delegate.encode(rawPassword);
    }

    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return PasswordInputPolicy.isSupportedLength(rawPassword) && delegate.matches(rawPassword, encodedPassword);
    }

    @Override public boolean upgradeEncoding(String encodedPassword) { return delegate.upgradeEncoding(encodedPassword); }
}
