package dev.wasdevv.minierp.exception;

/** A stock receipt or adjustment that cannot be applied. The message is user-facing (pt-BR). */
public class StockRejectedException extends RuntimeException {

    public StockRejectedException(String message) {
        super(message);
    }
}
