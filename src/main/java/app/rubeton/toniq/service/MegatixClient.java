package app.rubeton.toniq.service;

public interface MegatixClient {

    String fetchEventDetails(String eventId);

    String fetchTicketTiers(String eventId);

    String fetchAvailability(String eventId);
}
