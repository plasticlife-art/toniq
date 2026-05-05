package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.service.EventSyncService;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventSyncServiceImpl implements EventSyncService {

    private final DataManager dataManager;

    @Override
    public Event importEvent(final String eventId) {
        throw new UnsupportedOperationException("Megatix event import is not implemented in Stage 1");
    }

    @Transactional
    @Override
    public EventSyncState ensureSyncState(final Event event) {
        Objects.requireNonNull(event, "event must not be null");
        EventSyncState existing = dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .optional()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        EventSyncState syncState = dataManager.create(EventSyncState.class);
        syncState.setEvent(event);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncState.setCreatedAt(now);
        syncState.setUpdatedAt(now);
        return dataManager.save(syncState);
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncStarted(final String megatixEventId, final Event event,
                                          final SyncLogTriggerSource triggerSource, final SyncLogScope syncScope,
                                          final String requestPayloadJson) {
        EventSyncLog syncLog = dataManager.create(EventSyncLog.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event);
        syncLog.setMegatixEventId(megatixEventId);
        syncLog.setTriggerSource(triggerSource);
        syncLog.setSyncScope(syncScope);
        syncLog.setStatus(SyncLogStatus.STARTED);
        syncLog.setRequestPayloadJson(requestPayloadJson);
        syncLog.setStartedAt(now);
        syncLog.setCreatedAt(now);
        return dataManager.save(syncLog);
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncSuccess(final EventSyncLog syncLog, final Event event, final String responsePayloadJson) {
        Objects.requireNonNull(syncLog, "syncLog must not be null");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event != null ? event : syncLog.getEvent());
        syncLog.setStatus(SyncLogStatus.SUCCESS);
        syncLog.setResponsePayloadJson(responsePayloadJson);
        syncLog.setFinishedAt(now);
        EventSyncLog savedLog = dataManager.save(syncLog);

        if (savedLog.getEvent() != null) {
            EventSyncState syncState = ensureSyncState(savedLog.getEvent());
            syncState.setLastSyncedAt(now);
            syncState.setLastSyncResult(SyncResult.SUCCESS);
            syncState.setLastSyncError(null);
            if (savedLog.getSyncScope() == SyncLogScope.AVAILABILITY_REFRESH) {
                syncState.setLastAvailabilitySyncAt(now);
            } else {
                syncState.setLastEventDataSyncAt(now);
            }
            syncState.setUpdatedAt(now);
            dataManager.save(syncState);
        }

        return savedLog;
    }

    @Transactional
    @Override
    public EventSyncLog recordSyncFailure(final EventSyncLog syncLog, final Event event, final String errorCode,
                                          final String errorMessage, final String responsePayloadJson) {
        Objects.requireNonNull(syncLog, "syncLog must not be null");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        syncLog.setEvent(event != null ? event : syncLog.getEvent());
        syncLog.setStatus(SyncLogStatus.FAILURE);
        syncLog.setErrorCode(errorCode);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setResponsePayloadJson(responsePayloadJson);
        syncLog.setFinishedAt(now);
        EventSyncLog savedLog = dataManager.save(syncLog);

        if (savedLog.getEvent() != null) {
            EventSyncState syncState = ensureSyncState(savedLog.getEvent());
            syncState.setLastSyncedAt(now);
            syncState.setLastSyncResult(SyncResult.FAILURE);
            syncState.setLastSyncError(errorMessage);
            syncState.setUpdatedAt(now);
            dataManager.save(syncState);
        }

        return savedLog;
    }
}
