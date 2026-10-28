package dev.wasdevv.minierp.model;

public enum StockMovementType {
    INITIAL("Estoque inicial"),
    RECEIPT("Entrada"),
    ADJUSTMENT("Ajuste"),
    SALE("Venda"),
    CANCELLATION("Cancelamento");

    private final String label;

    StockMovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
