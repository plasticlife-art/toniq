package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.service.MegatixClient;
import org.springframework.stereotype.Component;

@Component
public class StubMegatixClient implements MegatixClient {

    @Override
    public String fetchEventDetails(final String eventId) {
        throw new UnsupportedOperationException("Megatix integration is not implemented in Stage 1");
    }

    @Override
    public String fetchEventPromoter(final String eventId) {
        throw new UnsupportedOperationException("Megatix integration is not implemented in Stage 1");
    }
}
