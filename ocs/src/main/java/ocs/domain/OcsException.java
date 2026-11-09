package ocs.domain;

public final class OcsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OcsException(String message) {
        super(message);
    }
}
