package app.rubeton.toniq.event;

import app.rubeton.toniq.entity.OverrideStatus;
import app.rubeton.toniq.entity.PublicationState;
import app.rubeton.toniq.entity.SyncLogScope;
import app.rubeton.toniq.entity.SyncLogStatus;
import app.rubeton.toniq.entity.SyncLogTriggerSource;
import app.rubeton.toniq.entity.SyncResult;
import app.rubeton.toniq.entity.TierAvailabilityState;
import io.jmix.core.Messages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EnumLocalizationTest {

    @Autowired
    Messages messages;

    @Test
    void test_eventEnumsHaveLocalizedCaptions() {
        assertThat(messages.getMessage(PublicationState.DRAFT, Locale.ENGLISH)).isEqualTo("Draft");
        assertThat(messages.getMessage(PublicationState.PUBLISHED, Locale.ENGLISH)).isEqualTo("Published");
        assertThat(messages.getMessage(PublicationState.UNPUBLISHED, Locale.ENGLISH)).isEqualTo("Unpublished");

        assertThat(messages.getMessage(SyncResult.SUCCESS, Locale.ENGLISH)).isEqualTo("Success");
        assertThat(messages.getMessage(SyncResult.FAILURE, Locale.ENGLISH)).isEqualTo("Failure");

        assertThat(messages.getMessage(SyncLogStatus.STARTED, Locale.ENGLISH)).isEqualTo("Started");
        assertThat(messages.getMessage(SyncLogStatus.SUCCESS, Locale.ENGLISH)).isEqualTo("Success");
        assertThat(messages.getMessage(SyncLogStatus.FAILURE, Locale.ENGLISH)).isEqualTo("Failure");
        assertThat(messages.getMessage(SyncLogStatus.IGNORED, Locale.ENGLISH)).isEqualTo("Ignored");

        assertThat(messages.getMessage(SyncLogScope.FULL_IMPORT, Locale.ENGLISH)).isEqualTo("Full import");
        assertThat(messages.getMessage(SyncLogScope.EVENT_REFRESH, Locale.ENGLISH)).isEqualTo("Event refresh");
        assertThat(messages.getMessage(SyncLogScope.AVAILABILITY_REFRESH, Locale.ENGLISH)).isEqualTo("Availability refresh");
        assertThat(messages.getMessage(SyncLogScope.UNPUBLISH, Locale.ENGLISH)).isEqualTo("Unpublish");
        assertThat(messages.getMessage(SyncLogScope.WEBHOOK_AUDIT, Locale.ENGLISH)).isEqualTo("Webhook audit");

        assertThat(messages.getMessage(SyncLogTriggerSource.WEBHOOK_ENABLE, Locale.ENGLISH)).isEqualTo("Webhook enable");
        assertThat(messages.getMessage(SyncLogTriggerSource.WEBHOOK_DISABLE, Locale.ENGLISH)).isEqualTo("Webhook disable");
        assertThat(messages.getMessage(SyncLogTriggerSource.WEBHOOK_UNSUPPORTED, Locale.ENGLISH)).isEqualTo("Webhook unsupported");
        assertThat(messages.getMessage(SyncLogTriggerSource.POLLING, Locale.ENGLISH)).isEqualTo("Polling");
        assertThat(messages.getMessage(SyncLogTriggerSource.MANUAL, Locale.ENGLISH)).isEqualTo("Manual");

        assertThat(messages.getMessage(OverrideStatus.COMPLETED, Locale.ENGLISH)).isEqualTo("Completed");
        assertThat(messages.getMessage(OverrideStatus.CANCELLED, Locale.ENGLISH)).isEqualTo("Cancelled");
        assertThat(messages.getMessage(OverrideStatus.RESCHEDULED, Locale.ENGLISH)).isEqualTo("Rescheduled");

        assertThat(messages.getMessage(TierAvailabilityState.AVAILABLE, Locale.ENGLISH)).isEqualTo("Available");
        assertThat(messages.getMessage(TierAvailabilityState.LOW, Locale.ENGLISH)).isEqualTo("Low");
        assertThat(messages.getMessage(TierAvailabilityState.SOLD_OUT, Locale.ENGLISH)).isEqualTo("Sold out");
        assertThat(messages.getMessage(TierAvailabilityState.UNKNOWN, Locale.ENGLISH)).isEqualTo("Unknown");
    }
}
