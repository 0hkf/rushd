package com.rushd.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PropertyNeedsValidator.class)
public @interface ConsistentPropertyNeeds {
    String message() default "متطلبات العقار غير متسقة";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
