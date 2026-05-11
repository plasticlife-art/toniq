package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventTicketTier;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.TierAvailabilityState;
import app.rubeton.toniq.service.AvailabilityRefreshService;
import app.rubeton.toniq.service.EventExecutionLockService;
import app.rubeton.toniq.service.EventRefreshInProgressException;
import app.rubeton.toniq.service.MegatixClient;
import app.rubeton.toniq.service.EventSyncService;
import app.rubeton.toniq.service.megatix.mapper.MegatixImportMapper;
import app.rubeton.toniq.service.megatix.model.ImportedTicketTierData;
import app.rubeton.toniq.service.megatix.model.MegatixEventDetailsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityRefreshServiceImpl implements AvailabilityRefreshService {

    private final DataManager dataManager;
    private final EventSyncService eventSyncService;
    private final EventExecutionLockService eventExecutionLockService;
    private final MegatixClient megatixClient;
    private final MegatixImportMapper megatixImportMapper;
    private final ObjectMapper objectMapper;
    private final SystemAuthenticator systemAuthenticator;

    @Override
    public void refreshAvailability(final String eventId) {
        refreshAvailability(eventId, SyncLogTriggerSource.MANUAL, "{\"source\":\"manual_live_refresh\"}");
    }

    @Override
    public void refreshAvailability(final String eventId, final SyncLogTriggerSource triggerSource,
                                    final String requestPayloadJson) {
        systemAuthenticator.runWithSystem(() -> doRefreshAvailability(eventId, triggerSource, requestPayloadJson));
    }

    private void doRefreshAvailability(final String eventId, final SyncLogTriggerSource triggerSource,
                                       final String requestPayloadJson) {
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent, triggerSource,
                SyncLogScope.AVAILABILITY_REFRESH, requestPayloadJson);

        try (EventExecutionLockService.EventExecutionLockHandle lockHandle = eventExecutionLockService.tryAcquire(eventId)) {
            if (!lockHandle.acquired()) {
                eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                        "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
                throw new EventRefreshInProgressException(eventId);
            }

            if (existingEvent == null) {
                eventSyncService.recordSyncIgnored(syncLog, null, "event_not_found",
                        "Cannot refresh availability for unknown event " + eventId, "{\"outcome\":\"missing_event\"}");
                return;
            }

            MegatixEventDetailsResponse eventResponse = megatixClient.fetchEventDetails(eventId);
            applyAvailabilitySnapshot(existingEvent, eventResponse, nowUtc());
            eventSyncService.recordSyncSuccess(syncLog, existingEvent, "{\"outcome\":\"availability_refreshed\"}");
        } catch (RuntimeException e) {
            log.error("Availability refresh failed for event {}", eventId, e);
            Event currentEvent = findEvent(eventId);
            eventSyncService.recordSyncFailure(syncLog, currentEvent != null ? currentEvent : existingEvent,
                    "availability_refresh_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
            throw e;
        }
    }

    @Transactional
    protected void applyAvailabilitySnapshot(final Event event, final MegatixEventDetailsResponse eventResponse,
                                             final OffsetDateTime refreshedAt) {
        List<EventTicketTier> tiers = dataManager.load(EventTicketTier.class)
                .query("e.event = ?1", event)
                .list();
        Map<String, EventTicketTier> tierByMegatixId = new HashMap<>();
        for (EventTicketTier tier : tiers) {
            tierByMegatixId.put(tier.getMegatixTierId(), tier);
        }

        SaveContext saveContext = new SaveContext();
        for (var ticket : eventResponse.payload().getTickets()) {
            ImportedTicketTierData tierData = megatixImportMapper.toImportedTicketTierData(ticket, serialize(ticket));
            EventTicketTier tier = tierByMegatixId.get(tierData.getMegatixTierId());
            if (tier == null) {
                continue;
            }
            tier.setAvailabilityCount(tierData.getAvailabilityCount());
            tier.setAvailabilityState(tierData.getAvailabilityState());
            tier.setLastAvailabilitySyncAt(refreshedAt);
            tier.setRawPayloadJson(tierData.getRawPayloadJson());
            tier.setUpdatedAt(refreshedAt);
            saveContext.saving(tier);
        }

        if (!saveContext.getEntitiesToSave().isEmpty()) {
            dataManager.save(saveContext);
        }
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

    private Event findEvent(final String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return dataManager.load(Event.class)
                .query("e.megatixEventId = ?1", eventId)
                .optional()
                .orElse(null);
    }

    private String serialize(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Megatix payload", e);
        }
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
