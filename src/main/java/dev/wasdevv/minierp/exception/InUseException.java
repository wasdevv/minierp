package dev.wasdevv.minierp.exception;

/** A record other records depend on, so it cannot be deleted. The message is user-facing (pt-BR). */
public class InUseException extends RuntimeException {

    public InUseException(String message) {
        super(message);
    }
}
