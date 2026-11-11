package com.rushd.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = PropertySpecificationsValidator.class)
public @interface ValidPropertySpecifications {
    String message() default "مواصفات العقار غير صالحة";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
