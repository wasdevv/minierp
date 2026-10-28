package dev.wasdevv.minierp.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("SKU already in use: " + sku);
    }
}
