package app.rubeton.toniq.service.megatix.model;

import app.rubeton.toniq.entity.SyncLogStatus;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
public class ManualSyncStatus {
    UUID syncLogId;
    SyncLogStatus status;
    String megatixEventId;
    UUID localEventId;
    OffsetDateTime finishedAt;
    String errorMessage;

    public boolean isTerminal() {
        return status == SyncLogStatus.SUCCESS || status == SyncLogStatus.FAILURE || status == SyncLogStatus.IGNORED;
    }
}
