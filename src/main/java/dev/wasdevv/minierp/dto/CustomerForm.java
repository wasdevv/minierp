package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.Phone;
import dev.wasdevv.minierp.model.TaxId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * What the customer form submits. E-mail is stored lower-case and the document as digits only, so
 * "529.982.247-25" and "52998224725" are the same customer.
 */
public class CustomerForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String name;

    @Email(message = "Informe um e-mail válido.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    private String email;

    @ValidTaxId
    private String document;

    @ValidPhone
    private String phone;

    public static CustomerForm from(Customer customer) {
        CustomerForm form = new CustomerForm();
        form.setName(customer.getName());
        form.setEmail(customer.getEmail());
        form.setDocument(customer.getDocument());
        form.setPhone(customer.getPhone());
        return form;
    }

    public void applyTo(Customer customer) {
        customer.setName(name);
        customer.setEmail(email);
        customer.setDocument(document);
        customer.setPhone(phone);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        String trimmed = trimToNull(email);
        this.email = trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Formatted for the form field; {@link #getDocumentDigits()} is what gets stored. */
    public String getDocument() {
        return TaxId.format(document);
    }

    public String getDocumentDigits() {
        return document;
    }

    /** Punctuation is dropped; anything else is kept so that validation rejects it instead of hiding it. */
    public void setDocument(String document) {
        String trimmed = trimToNull(document);
        this.document = trimmed != null && trimmed.matches("[\\d.\\-/\\s]+") ? TaxId.normalize(trimmed) : trimmed;
    }

    /** Formatted for the form field; {@link #getPhoneDigits()} is what gets stored. */
    public String getPhone() {
        return Phone.format(phone);
    }

    public String getPhoneDigits() {
        return phone;
    }

    /** Punctuation and +55 are dropped; letters are kept so that validation rejects them. */
    public void setPhone(String phone) {
        String trimmed = trimToNull(phone);
        this.phone = trimmed != null && trimmed.matches("[\\d\\s()+.\\-]+") ? Phone.normalize(trimmed) : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
