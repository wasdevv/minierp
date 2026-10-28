package dev.wasdevv.minierp.exception;

/** The product changed (a sale, another edit) after the form was opened. */
public class StaleProductException extends RuntimeException {

    public StaleProductException(Long id) {
        super("Product " + id + " changed since the form was loaded");
    }
}
