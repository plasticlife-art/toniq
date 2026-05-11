package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.SyncLogTriggerSource;

public interface EventDataRefreshService {

    void refreshEventData(String eventId, SyncLogTriggerSource triggerSource, String requestPayloadJson);
}
