package com.rushd.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface ValidPasswordByteLength {
    String message() default "كلمة المرور يجب ألا تتجاوز 72 بايت بترميز UTF-8 (قد يشغل الحرف العربي أكثر من بايت)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
