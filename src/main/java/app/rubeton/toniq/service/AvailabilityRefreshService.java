package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.TierAvailabilityState;

import java.time.OffsetDateTime;

public interface AvailabilityRefreshService {

    void refreshAvailability(String eventId);

    EventTicketTier updateTierAvailability(EventTicketTier tier, Integer availabilityCount,
                                           TierAvailabilityState availabilityState, OffsetDateTime refreshedAt);
}
