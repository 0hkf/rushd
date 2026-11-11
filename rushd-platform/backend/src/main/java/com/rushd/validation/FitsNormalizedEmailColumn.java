package com.rushd.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NormalizedEmailLengthValidator.class)
public @interface FitsNormalizedEmailColumn {
    String message() default "البريد الإلكتروني بعد توحيد حالة الأحرف يجب ألا يتجاوز 255 حرفًا";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
