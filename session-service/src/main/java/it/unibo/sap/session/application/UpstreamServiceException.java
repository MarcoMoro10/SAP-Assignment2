package it.unibo.sap.session.application;

public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public UpstreamServiceException(final String message) {
        super(message);
    }
}
