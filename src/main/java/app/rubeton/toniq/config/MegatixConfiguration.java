package app.rubeton.toniq.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MegatixProperties.class)
public class MegatixConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MegatixConfiguration.class);

    @Bean
    public MegatixIpv4DnsResolver megatixIpv4DnsResolver(final MegatixProperties megatixProperties) {
        MegatixIpv4DnsResolver resolver = new MegatixIpv4DnsResolver(megatixProperties.getBaseUrl());
        log.info("Megatix IPv4-only DNS enabled for hosts: {}", resolver.getIpv4OnlyHosts());
        return resolver;
    }

    @Bean
    public CloseableHttpClient megatixHttpClient(final MegatixIpv4DnsResolver megatixIpv4DnsResolver) {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(megatixIpv4DnsResolver)
                        .build())
                .build();
    }

    @Bean("megatixRestClient")
    public RestClient megatixRestClient(final MegatixProperties megatixProperties,
                                        final CloseableHttpClient megatixHttpClient) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(megatixHttpClient));

        String baseUrl = trimTrailingSlash(megatixProperties.getBaseUrl());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder = builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    private String trimTrailingSlash(final String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
