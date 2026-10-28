package dev.wasdevv.minierp.exception;

public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException() {
        super("Document already in use");
    }
}
