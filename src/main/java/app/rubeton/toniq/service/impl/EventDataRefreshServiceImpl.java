package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.service.EventDataRefreshService;
import app.rubeton.toniq.service.EventExecutionLockService;
import app.rubeton.toniq.service.EventRefreshInProgressException;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.EventSyncService;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventDataRefreshServiceImpl implements EventDataRefreshService {

    private final DataManager dataManager;
    private final EventSyncService eventSyncService;
    private final EventPublicationService eventPublicationService;
    private final EventExecutionLockService eventExecutionLockService;
    private final SystemAuthenticator systemAuthenticator;

    @Override
    public void refreshEventData(final String eventId, final SyncLogTriggerSource triggerSource, final String requestPayloadJson) {
        systemAuthenticator.runWithSystem(() -> doRefreshEventData(eventId, triggerSource, requestPayloadJson));
    }

    private void doRefreshEventData(final String eventId, final SyncLogTriggerSource triggerSource, final String requestPayloadJson) {
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent, triggerSource,
                SyncLogScope.EVENT_REFRESH, requestPayloadJson);

        try (EventExecutionLockService.EventExecutionLockHandle lockHandle = eventExecutionLockService.tryAcquire(eventId)) {
            if (!lockHandle.acquired()) {
                eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                        "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
                throw new EventRefreshInProgressException(eventId);
            }

            Event refreshedEvent = eventSyncService.importEvent(eventId);
            eventPublicationService.reconcile(refreshedEvent, nowUtc());
            eventSyncService.recordSyncSuccess(syncLog, refreshedEvent, "{\"outcome\":\"event_refreshed\"}");
        } catch (RuntimeException e) {
            log.error("Event data refresh failed for event {}", eventId, e);
            Event currentEvent = findEvent(eventId);
            eventSyncService.recordSyncFailure(syncLog, currentEvent != null ? currentEvent : existingEvent,
                    "event_refresh_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
            throw e;
        }
    }

    private Event findEvent(final String megatixEventId) {
        if (megatixEventId == null || megatixEventId.isBlank()) {
            return null;
        }
        return dataManager.load(Event.class)
                .query("e.megatixEventId = ?1", megatixEventId)
                .optional()
                .orElse(null);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
