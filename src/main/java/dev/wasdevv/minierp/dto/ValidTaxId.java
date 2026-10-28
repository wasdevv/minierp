package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.TaxId;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A CPF or CNPJ with valid check digits, as digits only. {@code null} is valid: the field is optional. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTaxId.Validator.class)
public @interface ValidTaxId {

    String message() default "Informe um CPF ou CNPJ válido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTaxId, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || TaxId.isValid(value);
        }
    }
}
