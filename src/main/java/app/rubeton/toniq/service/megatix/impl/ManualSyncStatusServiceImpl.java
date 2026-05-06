package app.rubeton.toniq.service.megatix.impl;

import app.rubeton.toniq.entity.EventSyncLog;
import app.rubeton.toniq.service.megatix.ManualSyncStatusService;
import app.rubeton.toniq.service.megatix.model.ManualSyncStatus;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManualSyncStatusServiceImpl implements ManualSyncStatusService {

    private final DataManager dataManager;

    @Override
    @Transactional(readOnly = true)
    public ManualSyncStatus getStatus(final UUID syncLogId) {
        EventSyncLog syncLog = dataManager.load(EventSyncLog.class)
                .id(syncLogId)
                .one();

        UUID localEventId = syncLog.getEvent() != null ? syncLog.getEvent().getId() : null;
        return new ManualSyncStatus(
                syncLog.getId(),
                syncLog.getStatus(),
                syncLog.getMegatixEventId(),
                localEventId,
                syncLog.getFinishedAt(),
                syncLog.getErrorMessage()
        );
    }
}
