package dev.wasdevv.minierp.exception;

import java.util.List;

/** A sale that breaks business rules. The messages are user-facing (pt-BR). */
public class SaleRejectedException extends RuntimeException {

    private final List<String> reasons;

    public SaleRejectedException(List<String> reasons) {
        super(String.join(" ", reasons));
        this.reasons = List.copyOf(reasons);
    }

    public SaleRejectedException(String reason) {
        this(List.of(reason));
    }

    public List<String> getReasons() {
        return reasons;
    }
}
