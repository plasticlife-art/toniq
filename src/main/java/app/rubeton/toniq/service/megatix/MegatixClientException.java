package app.rubeton.toniq.service.megatix;

public class MegatixClientException extends RuntimeException {

    private final Integer statusCode;

    public MegatixClientException(final String message) {
        this(message, null, null);
    }

    public MegatixClientException(final String message, final Throwable cause) {
        this(message, null, cause);
    }

    public MegatixClientException(final String message, final Integer statusCode, final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
