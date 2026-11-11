package com.rushd.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Decimal shape validation without integer overflow or conversion to an expanded string. */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeDecimalPrecisionValidator.class)
public @interface SafeDecimalPrecision {
    String message() default "القيمة تتجاوز عدد الخانات أو المنازل العشرية المسموح";
    int integer();
    int fraction();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
