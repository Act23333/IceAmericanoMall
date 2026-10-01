package org.icedamericanomall.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.icedamericanomall.common.annotation.Phone;

public class PhoneValidator implements ConstraintValidator<Phone, String> {
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // 空值校验交给 @NotBlank 处理
        }
        return phone.matches(PHONE_REGEX);
    }
}