package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.entity.Event;
import app.rubeton.toniq.entity.EventSyncState;
import app.rubeton.toniq.service.SroastProjectionService;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SroastProjectionServiceImpl implements SroastProjectionService {

    private final DataManager dataManager;

    @Override
    public boolean existsForEvent(final Event event) {
        if (event == null || event.getId() == null) {
            return false;
        }
        if (event.getRawPayloadJson() != null && !event.getRawPayloadJson().isBlank()) {
            return true;
        }

        EventSyncState syncState = dataManager.load(EventSyncState.class)
                .query("e.event = ?1", event)
                .optional()
                .orElse(null);
        return syncState != null && syncState.getLastEventDataSyncAt() != null;
    }
}
