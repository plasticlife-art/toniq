package app.rubeton.toniq.service.megatix.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.service.EventPublicationService;
import app.rubeton.toniq.service.EventSyncService;
import app.rubeton.toniq.service.megatix.MegatixSyncCoordinator;
import app.rubeton.toniq.service.megatix.model.ManualSyncHandle;
import app.rubeton.toniq.service.megatix.model.MegatixWebhookCommand;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class MegatixSyncCoordinatorImpl implements MegatixSyncCoordinator {

    private final EventSyncService eventSyncService;
    private final EventPublicationService eventPublicationService;
    private final DataManager dataManager;
    private final TaskExecutor megatixSyncTaskExecutor;
    private final SystemAuthenticator systemAuthenticator;

    private final ConcurrentHashMap<String, EventLockEntry> eventLocks = new ConcurrentHashMap<>();

    @Override
    public void submitWebhook(final MegatixWebhookCommand command) {
        megatixSyncTaskExecutor.execute(() -> systemAuthenticator.runWithSystem(() -> {
            if (Boolean.TRUE.equals(command.getCryptoEnabled())) {
                runImportFlow(command.getMegatixEventId(), SyncLogTriggerSource.WEBHOOK_ENABLE,
                        SyncLogScope.FULL_IMPORT, command.getRawPayloadJson(), Boolean.TRUE);
                return;
            }
            runDisableFlow(command.getMegatixEventId(), command.getRawPayloadJson());
        }));
    }

    @Override
    public ManualSyncHandle submitManualImport(final String eventId) {
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = systemAuthenticator.withSystem(() -> eventSyncService.recordSyncStarted(
                eventId, existingEvent, SyncLogTriggerSource.MANUAL, SyncLogScope.FULL_IMPORT,
                "{\"source\":\"manual_import\"}"));
        megatixSyncTaskExecutor.execute(() -> systemAuthenticator.runWithSystem(() ->
                runImportFlow(syncLog.getId(), eventId, existingEvent, null)));
        return new ManualSyncHandle(syncLog.getId());
    }

    @Override
    public ManualSyncHandle submitManualResync(final String eventId) {
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = systemAuthenticator.withSystem(() -> eventSyncService.recordSyncStarted(
                eventId, existingEvent, SyncLogTriggerSource.MANUAL, SyncLogScope.EVENT_REFRESH,
                "{\"source\":\"manual_resync\"}"));
        megatixSyncTaskExecutor.execute(() -> systemAuthenticator.runWithSystem(() ->
                runImportFlow(syncLog.getId(), eventId, existingEvent, null)));
        return new ManualSyncHandle(syncLog.getId());
    }

    @Override
    public void recordUnsupportedWebhook(final MegatixWebhookCommand command) {
        systemAuthenticator.runWithSystem(() -> {
            log.warn("Ignoring unsupported Megatix webhook event type: eventType={}, eventId={}",
                    command.getEventType(), command.getMegatixEventId());
            Event event = findEvent(command.getMegatixEventId());
            EventSyncLog syncLog = eventSyncService.recordSyncStarted(command.getMegatixEventId(), event,
                    SyncLogTriggerSource.WEBHOOK_UNSUPPORTED, SyncLogScope.WEBHOOK_AUDIT, command.getRawPayloadJson());
            eventSyncService.recordSyncIgnored(syncLog, event, "unsupported_webhook_event",
                    "Unsupported Megatix webhook event type: " + command.getEventType(),
                    "{\"outcome\":\"ignored\"}");
        });
    }

    private void runImportFlow(final String eventId, final SyncLogTriggerSource triggerSource,
                               final SyncLogScope syncScope, final String requestPayloadJson,
                               final Boolean webhookEnabled) {
        EventLockEntry lockEntry = acquireEventLockEntry(eventId);
        ReentrantLock lock = lockEntry.lock();
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent, triggerSource, syncScope, requestPayloadJson);
        if (!lock.tryLock()) {
            eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                    "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
            releaseEventLockEntry(eventId, lockEntry);
            return;
        }

        try {
            Event importedEvent = eventSyncService.importEvent(eventId);
            if (webhookEnabled != null) {
                eventPublicationService.recordMegatixWebhookState(importedEvent, webhookEnabled,
                        webhookEnabled ? "megatix_webhook_enabled" : "megatix_webhook_disabled", nowUtc());
            }
            eventPublicationService.reconcile(importedEvent, nowUtc());
            eventSyncService.recordSyncSuccess(syncLog, importedEvent, "{\"outcome\":\"imported\"}");
        } catch (RuntimeException e) {
            log.error("Megatix import failed for event {}", eventId, e);
            Event currentEvent = findEvent(eventId);
            eventSyncService.recordSyncFailure(syncLog, currentEvent != null ? currentEvent : existingEvent,
                    "import_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
        } finally {
            lock.unlock();
            releaseEventLockEntry(eventId, lockEntry);
        }
    }

    private void runImportFlow(final java.util.UUID syncLogId, final String eventId,
                               final Event existingEvent, final Boolean webhookEnabled) {
        EventLockEntry lockEntry = acquireEventLockEntry(eventId);
        ReentrantLock lock = lockEntry.lock();
        EventSyncLog syncLog = dataManager.load(EventSyncLog.class).id(syncLogId).one();
        if (!lock.tryLock()) {
            eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                    "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
            releaseEventLockEntry(eventId, lockEntry);
            return;
        }

        try {
            Event importedEvent = eventSyncService.importEvent(eventId);
            if (webhookEnabled != null) {
                eventPublicationService.recordMegatixWebhookState(importedEvent, webhookEnabled,
                        webhookEnabled ? "megatix_webhook_enabled" : "megatix_webhook_disabled", nowUtc());
            }
            eventPublicationService.reconcile(importedEvent, nowUtc());
            eventSyncService.recordSyncSuccess(syncLog, importedEvent, "{\"outcome\":\"imported\"}");
        } catch (RuntimeException e) {
            log.error("Megatix import failed for event {}", eventId, e);
            Event currentEvent = findEvent(eventId);
            eventSyncService.recordSyncFailure(syncLog, currentEvent != null ? currentEvent : existingEvent,
                    "import_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
        } finally {
            lock.unlock();
            releaseEventLockEntry(eventId, lockEntry);
        }
    }

    private void runDisableFlow(final String eventId, final String requestPayloadJson) {
        EventLockEntry lockEntry = acquireEventLockEntry(eventId);
        ReentrantLock lock = lockEntry.lock();
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent,
                SyncLogTriggerSource.WEBHOOK_DISABLE, SyncLogScope.UNPUBLISH, requestPayloadJson);
        if (!lock.tryLock()) {
            eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                    "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
            releaseEventLockEntry(eventId, lockEntry);
            return;
        }

        try {
            if (existingEvent != null) {
                eventPublicationService.recordMegatixWebhookState(existingEvent, false, "megatix_webhook_disabled", nowUtc());
                eventPublicationService.reconcile(existingEvent, nowUtc());
            }
            eventSyncService.recordSyncSuccess(syncLog, existingEvent, "{\"outcome\":\"unpublished\"}");
        } catch (RuntimeException e) {
            log.error("Megatix unpublish failed for event {}", eventId, e);
            eventSyncService.recordSyncFailure(syncLog, existingEvent,
                    "unpublish_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
        } finally {
            lock.unlock();
            releaseEventLockEntry(eventId, lockEntry);
        }
    }

    int activeEventLockCount() {
        return eventLocks.size();
    }

    private EventLockEntry acquireEventLockEntry(final String eventId) {
        return eventLocks.compute(eventId, (ignored, existing) -> {
            EventLockEntry entry = existing != null ? existing : new EventLockEntry();
            entry.retain();
            return entry;
        });
    }

    private void releaseEventLockEntry(final String eventId, final EventLockEntry expectedEntry) {
        eventLocks.computeIfPresent(eventId, (ignored, existing) -> {
            if (existing != expectedEntry) {
                return existing;
            }
            int references = existing.release();
            if (references < 0) {
                throw new IllegalStateException("Event lock ref-count became negative for event " + eventId);
            }
            return references == 0 ? null : existing;
        });
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

    private static final class EventLockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();

        private ReentrantLock lock() {
            return lock;
        }

        private void retain() {
            references.incrementAndGet();
        }

        private int release() {
            return references.decrementAndGet();
        }
    }
}
