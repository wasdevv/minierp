package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.Phone;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A Brazilian phone with area code, as digits. {@code null} is valid: the field is optional. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPhone.Validator.class)
public @interface ValidPhone {

    String message() default "Informe o telefone com DDD (ex.: (11) 98765-4321).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Phone.isValid(value);
        }
    }
}
