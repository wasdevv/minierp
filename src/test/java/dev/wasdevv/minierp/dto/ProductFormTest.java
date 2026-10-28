package dev.wasdevv.minierp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProductFormTest {

    private static jakarta.validation.ValidatorFactory factory;
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
    void normalizesText() {
        ProductForm form = valid();
        form.setName("  Caneta azul  ");
        form.setSku("  cn-01 ");
        form.setDescription("   ");

        assertThat(form.getName()).isEqualTo("Caneta azul");
        assertThat(form.getSku()).isEqualTo("CN-01");
        assertThat(form.getDescription()).isNull();
    }

    @Test
    void acceptsValidForm() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void acceptsZeroPriceAndZeroStock() {
        ProductForm form = valid();
        form.setPrice(BigDecimal.ZERO);
        form.setStockQuantity(0);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void rejectsBlankRequiredFields() {
        ProductForm form = new ProductForm();
        form.setName("   ");
        form.setSku("");
        form.setStockQuantity(null);

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("name", "sku", "price", "stockQuantity");
    }

    @Test
    void rejectsNegativePrice() {
        ProductForm form = valid();
        form.setPrice(new BigDecimal("-0.01"));

        assertThat(invalidFields(form)).containsExactly("price");
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        ProductForm form = valid();
        form.setPrice(new BigDecimal("1.999"));

        assertThat(invalidFields(form)).containsExactly("price");
    }

    @Test
    void rejectsNegativeStock() {
        ProductForm form = valid();
        form.setStockQuantity(-1);

        assertThat(invalidFields(form)).containsExactly("stockQuantity");
    }

    @Test
    void rejectsSkuWithSpacesOrSymbols() {
        ProductForm form = valid();
        form.setSku("CN 01");

        assertThat(invalidFields(form)).containsExactly("sku");
    }

    @Test
    void rejectsTooLongName() {
        ProductForm form = valid();
        form.setName("x".repeat(121));

        assertThat(invalidFields(form)).containsExactly("name");
    }

    static ProductForm valid() {
        ProductForm form = new ProductForm();
        form.setName("Caneta azul");
        form.setSku("CN-01");
        form.setPrice(new BigDecimal("2.50"));
        form.setStockQuantity(10);
        return form;
    }

    private static Set<String> invalidFields(ProductForm form) {
        Set<ConstraintViolation<ProductForm>> violations = validator.validate(form);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
