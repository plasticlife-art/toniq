package app.rubeton.toniq.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "megatix")
@Getter
@Setter
public class MegatixProperties {

    private String baseUrl;
    private String email;
    private String password;
    private String webhookSignature;
    private long tokenRefreshSkewSeconds = 30;
}
