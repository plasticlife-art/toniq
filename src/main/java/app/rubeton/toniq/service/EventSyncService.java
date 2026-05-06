package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogTriggerSource;

public interface EventSyncService {

    Event importEvent(String eventId);

    EventSyncState ensureSyncState(Event event);

    EventSyncLog recordSyncStarted(String megatixEventId, Event event, SyncLogTriggerSource triggerSource,
                                   SyncLogScope syncScope, String requestPayloadJson);

    EventSyncLog recordSyncSuccess(EventSyncLog syncLog, Event event, String responsePayloadJson);

    EventSyncLog recordSyncFailure(EventSyncLog syncLog, Event event, String errorCode, String errorMessage,
                                   String responsePayloadJson);

    EventSyncLog recordSyncIgnored(EventSyncLog syncLog, Event event, String reasonCode, String reasonMessage,
                                   String responsePayloadJson);
}
