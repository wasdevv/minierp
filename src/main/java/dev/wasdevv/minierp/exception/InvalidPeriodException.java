package dev.wasdevv.minierp.exception;

/** A report period that cannot be used. The message is user-facing (pt-BR). */
public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(String message) {
        super(message);
    }
}
