package com.rushd.validation;

import com.rushd.security.PasswordInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordByteLengthValidator implements ConstraintValidator<ValidPasswordByteLength, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        // Requiredness is deliberately owned by @NotBlank.
        return value == null || PasswordInputPolicy.isSupportedLength(value);
    }
}
