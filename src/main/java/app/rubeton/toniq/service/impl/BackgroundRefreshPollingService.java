package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.service.AvailabilityRefreshService;
import app.rubeton.toniq.service.EventDataRefreshService;
import app.rubeton.toniq.service.EventRefreshInProgressException;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundRefreshPollingService {

    private final DataManager dataManager;
    private final EventDataRefreshService eventDataRefreshService;
    private final AvailabilityRefreshService availabilityRefreshService;
    private final SystemAuthenticator systemAuthenticator;

    public void refreshPublishedEventData() {
        List<String> eventIds = systemAuthenticator.withSystem(this::loadEligibleMegatixEventIds);
        for (String eventId : eventIds) {
            try {
                eventDataRefreshService.refreshEventData(eventId, SyncLogTriggerSource.POLLING,
                        "{\"source\":\"background_metadata_poll\"}");
            } catch (EventRefreshInProgressException e) {
                log.debug("Skipping metadata refresh for {} because another sync is active", eventId);
            } catch (RuntimeException e) {
                log.warn("Background event-data refresh failed for {}", eventId, e);
            }
        }
    }

    public void refreshPublishedAvailability() {
        List<String> eventIds = systemAuthenticator.withSystem(this::loadEligibleMegatixEventIds);
        for (String eventId : eventIds) {
            try {
                availabilityRefreshService.refreshAvailability(eventId, SyncLogTriggerSource.POLLING,
                        "{\"source\":\"background_availability_poll\"}");
            } catch (EventRefreshInProgressException e) {
                log.debug("Skipping availability refresh for {} because another sync is active", eventId);
            } catch (RuntimeException e) {
                log.warn("Background availability refresh failed for {}", eventId, e);
            }
        }
    }

    private List<String> loadEligibleMegatixEventIds() {
        return dataManager.load(EventPublicationSettings.class)
                .query("e.cryptoEnabled = true and e.publicationState = app.rubeton.toniq.entity.PublicationState.PUBLISHED "
                        + "and e.event.deletedAt is null")
                .list()
                .stream()
                .map(EventPublicationSettings::getEvent)
                .filter(java.util.Objects::nonNull)
                .map(event -> event.getMegatixEventId())
                .filter(eventId -> eventId != null && !eventId.isBlank())
                .toList();
    }
}
