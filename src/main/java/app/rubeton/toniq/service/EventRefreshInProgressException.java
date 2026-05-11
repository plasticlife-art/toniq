package app.rubeton.toniq.service;

public class EventRefreshInProgressException extends RuntimeException {

    public EventRefreshInProgressException(final String eventId) {
        super("Refresh already running for event " + eventId);
    }
}
