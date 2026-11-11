package com.rushd.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class SafeDecimalPrecisionValidator implements ConstraintValidator<SafeDecimalPrecision, BigDecimal> {
    private int integerLimit;
    private int fractionLimit;

    @Override
    public void initialize(SafeDecimalPrecision constraint) {
        integerLimit = constraint.integer();
        fractionLimit = constraint.fraction();
        if (integerLimit < 1 || fractionLimit < 0) {
            throw new IllegalArgumentException("Decimal precision limits must be positive integer and nonnegative fraction");
        }
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return withinLimits(value, integerLimit, fractionLimit);
    }

    /** Shared by opt-in structured decimal validation, without changing JSON globally. */
    public static boolean withinLimits(BigDecimal value, int integerLimit, int fractionLimit) {
        if (value == null) {
            return true;
        }
        // Cast before subtraction. In particular, precision=1 and scale=-2147483647
        // must not wrap around and accidentally admit an enormous exponent, even for zero.
        long integerDigits = Math.max((long) value.precision() - (long) value.scale(), 0L);
        long fractionDigits = Math.max((long) value.scale(), 0L);
        return integerDigits <= integerLimit && fractionDigits <= fractionLimit;
    }
}
