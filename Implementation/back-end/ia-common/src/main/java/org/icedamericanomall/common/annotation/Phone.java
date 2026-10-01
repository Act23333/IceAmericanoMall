package org.icedamericanomall.common.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.icedamericanomall.common.validator.PhoneValidator;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
public @interface Phone {
    String message() default "手机号格式不正确";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}