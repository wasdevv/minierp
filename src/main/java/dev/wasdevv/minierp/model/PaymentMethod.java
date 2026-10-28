package dev.wasdevv.minierp.model;

public enum PaymentMethod {
    PIX("Pix"),
    CASH("Dinheiro"),
    DEBIT_CARD("Cartão de débito"),
    CREDIT_CARD("Cartão de crédito"),
    /** "Fiado": paid later, so the sale needs a customer. */
    ON_ACCOUNT("Fiado");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
