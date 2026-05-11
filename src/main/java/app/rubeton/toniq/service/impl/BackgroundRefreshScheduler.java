package app.rubeton.toniq.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "toniq.background-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundRefreshScheduler {

    private final BackgroundRefreshPollingService backgroundRefreshPollingService;

    public BackgroundRefreshScheduler(final BackgroundRefreshPollingService backgroundRefreshPollingService) {
        this.backgroundRefreshPollingService = backgroundRefreshPollingService;
    }

    @Scheduled(fixedDelayString = "${toniq.background-refresh.metadata-fixed-delay-ms:600000}")
    public void refreshEventData() {
        backgroundRefreshPollingService.refreshPublishedEventData();
    }

    @Scheduled(fixedDelayString = "${toniq.background-refresh.availability-fixed-delay-ms:180000}")
    public void refreshAvailability() {
        backgroundRefreshPollingService.refreshPublishedAvailability();
    }
}
