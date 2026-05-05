package app.rubeton.toniq.service;

public interface MegatixClient {

    String fetchEventDetails(String eventId);

    String fetchEventPromoter(String eventId);
}
