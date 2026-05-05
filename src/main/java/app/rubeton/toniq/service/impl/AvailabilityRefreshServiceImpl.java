package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.service.AvailabilityRefreshService;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AvailabilityRefreshServiceImpl implements AvailabilityRefreshService {

    private final DataManager dataManager;

    @Override
    public void refreshAvailability(final String eventId) {
        throw new UnsupportedOperationException("Megatix availability refresh is not implemented in Stage 1");
    }

    @Transactional
    @Override
    public EventTicketTier updateTierAvailability(final EventTicketTier tier, final Integer availabilityCount,
                                                  final TierAvailabilityState availabilityState, final OffsetDateTime refreshedAt) {
        OffsetDateTime effectiveRefreshedAt = refreshedAt != null ? refreshedAt : OffsetDateTime.now(ZoneOffset.UTC);
        tier.setAvailabilityCount(availabilityCount);
        tier.setAvailabilityState(availabilityState);
        tier.setLastAvailabilitySyncAt(effectiveRefreshedAt);
        tier.setUpdatedAt(effectiveRefreshedAt);
        return dataManager.save(tier);
    }
}
