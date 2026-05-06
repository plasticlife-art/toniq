package app.rubeton.toniq.service;

import app.rubeton.toniq.service.megatix.model.MegatixEventDetailsResponse;
import app.rubeton.toniq.service.megatix.model.MegatixPromoterResponse;

public interface MegatixClient {

    MegatixEventDetailsResponse fetchEventDetails(String eventId);

    MegatixPromoterResponse fetchEventPromoter(String eventId);
}
