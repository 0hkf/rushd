package com.rushd.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;

public class NormalizedEmailLengthValidator implements ConstraintValidator<FitsNormalizedEmailColumn, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        // Match AuthService's existing normalization: Unicode lowercase can
        // expand one character (for example İ) into more than one code point.
        return value == null || value.trim().toLowerCase(Locale.ROOT).length() <= 255;
    }
}
