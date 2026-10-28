package dev.wasdevv.minierp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CustomerFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void normalizesInput() {
        CustomerForm form = new CustomerForm();
        form.setName("  Maria Souza ");
        form.setEmail(" Maria@Exemplo.COM ");
        form.setDocument(" 529.982.247-25 ");

        assertThat(form.getName()).isEqualTo("Maria Souza");
        assertThat(form.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(form.getDocumentDigits()).isEqualTo("52998224725");
        assertThat(form.getDocument()).isEqualTo("529.982.247-25");
    }

    @Test
    void emailAndDocumentAreOptional() {
        CustomerForm form = new CustomerForm();
        form.setName("Maria Souza");
        form.setEmail("  ");
        form.setDocument("");

        assertThat(validator.validate(form)).isEmpty();
        assertThat(form.getEmail()).isNull();
        assertThat(form.getDocumentDigits()).isNull();
    }

    @Test
    void acceptsCnpj() {
        CustomerForm form = valid();
        form.setDocument("11.222.333/0001-81");

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void rejectsMissingName() {
        CustomerForm form = valid();
        form.setName(" ");

        assertThat(invalidFields(form)).containsExactly("name");
    }

    @Test
    void rejectsInvalidEmail() {
        CustomerForm form = valid();
        form.setEmail("maria@");

        assertThat(invalidFields(form)).containsExactly("email");
    }

    @Test
    void rejectsDocumentWithWrongCheckDigit() {
        CustomerForm form = valid();
        form.setDocument("529.982.247-24");

        assertThat(invalidFields(form)).containsExactly("document");
    }

    @Test
    void rejectsDocumentWithLettersInsteadOfDroppingThem() {
        CustomerForm form = valid();
        form.setDocument("abc");

        assertThat(invalidFields(form)).containsExactly("document");
        assertThat(form.getDocument()).as("redisplays what was typed").isEqualTo("abc");
    }

    private static CustomerForm valid() {
        CustomerForm form = new CustomerForm();
        form.setName("Maria Souza");
        form.setEmail("maria@exemplo.com");
        form.setDocument("52998224725");
        return form;
    }

    private static Set<String> invalidFields(CustomerForm form) {
        return validator.validate(form).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
