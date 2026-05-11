package app.rubeton.toniq.service.megatix.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventPublicationSettings;
import app.rubeton.toniq.entity.PublicationMode;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.service.EventExecutionLockService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class MegatixSyncCoordinatorImpl implements MegatixSyncCoordinator {

    private final EventSyncService eventSyncService;
    private final EventPublicationService eventPublicationService;
    private final DataManager dataManager;
    private final TaskExecutor megatixSyncTaskExecutor;
    private final SystemAuthenticator systemAuthenticator;
    private final EventExecutionLockService eventExecutionLockService;

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
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent, triggerSource, syncScope, requestPayloadJson);
        try (EventExecutionLockService.EventExecutionLockHandle lockHandle = eventExecutionLockService.tryAcquire(eventId)) {
            if (!lockHandle.acquired()) {
                eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                        "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
                return;
            }
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
        }
    }

    private void runImportFlow(final java.util.UUID syncLogId, final String eventId,
                               final Event existingEvent, final Boolean webhookEnabled) {
        EventSyncLog syncLog = dataManager.load(EventSyncLog.class).id(syncLogId).one();
        try (EventExecutionLockService.EventExecutionLockHandle lockHandle = eventExecutionLockService.tryAcquire(eventId)) {
            if (!lockHandle.acquired()) {
                eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                        "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
                return;
            }
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
        }
    }

    private void runDisableFlow(final String eventId, final String requestPayloadJson) {
        Event existingEvent = findEvent(eventId);
        EventSyncLog syncLog = eventSyncService.recordSyncStarted(eventId, existingEvent,
                SyncLogTriggerSource.WEBHOOK_DISABLE, SyncLogScope.UNPUBLISH, requestPayloadJson);
        try (EventExecutionLockService.EventExecutionLockHandle lockHandle = eventExecutionLockService.tryAcquire(eventId)) {
            if (!lockHandle.acquired()) {
                eventSyncService.recordSyncIgnored(syncLog, existingEvent, "sync_in_progress",
                        "Sync already running for event " + eventId, "{\"outcome\":\"ignored_in_flight\"}");
                return;
            }
            if (existingEvent != null) {
                eventPublicationService.recordMegatixWebhookState(existingEvent, false, "megatix_webhook_disabled", nowUtc());
                eventPublicationService.reconcile(existingEvent, nowUtc());
            }
            eventSyncService.recordSyncSuccess(syncLog, existingEvent, "{\"outcome\":\"unpublished\"}");
        } catch (RuntimeException e) {
            log.error("Megatix unpublish failed for event {}", eventId, e);
            eventSyncService.recordSyncFailure(syncLog, existingEvent,
                    "unpublish_failed", e.getMessage(), "{\"outcome\":\"failed\"}");
        }
    }

    int activeEventLockCount() {
        return eventExecutionLockService.activeLockCount();
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
