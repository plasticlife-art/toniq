package app.rubeton.toniq.service.megatix;

import app.rubeton.toniq.service.megatix.model.ManualSyncStatus;

import java.util.UUID;

public interface ManualSyncStatusService {

    ManualSyncStatus getStatus(UUID syncLogId);
}
