package app.rubeton.toniq.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toniq.background-refresh")
@Getter
@Setter
public class BackgroundRefreshProperties {

    private boolean enabled = true;
    private long metadataFixedDelayMs = 600_000L;
    private long availabilityFixedDelayMs = 180_000L;
}
