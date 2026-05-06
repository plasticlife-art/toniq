package app.rubeton.toniq.service.megatix;

import app.rubeton.toniq.service.megatix.model.MegatixWebhookCommand;
import app.rubeton.toniq.service.megatix.model.ManualSyncHandle;

public interface MegatixSyncCoordinator {

    void submitWebhook(MegatixWebhookCommand command);

    ManualSyncHandle submitManualImport(String eventId);

    ManualSyncHandle submitManualResync(String eventId);

    void recordUnsupportedWebhook(MegatixWebhookCommand command);
}
