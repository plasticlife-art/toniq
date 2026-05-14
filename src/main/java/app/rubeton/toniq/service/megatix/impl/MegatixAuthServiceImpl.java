package app.rubeton.toniq.service.megatix.impl;

import app.rubeton.toniq.config.MegatixProperties;
import app.rubeton.toniq.service.megatix.MegatixAuthService;
import app.rubeton.toniq.service.megatix.MegatixClientException;
import app.rubeton.toniq.service.megatix.MegatixConfigurationException;
import app.rubeton.toniq.service.megatix.dto.MegatixLoginRequestDto;
import app.rubeton.toniq.service.megatix.dto.MegatixLoginResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class MegatixAuthServiceImpl implements MegatixAuthService {

    private final RestClient megatixRestClient;
    private final MegatixProperties megatixProperties;

    private volatile CachedToken cachedToken;

    public MegatixAuthServiceImpl(@Qualifier("megatixRestClient") final RestClient megatixRestClient,
                                  final MegatixProperties megatixProperties) {
        this.megatixRestClient = megatixRestClient;
        this.megatixProperties = megatixProperties;
    }

    @Override
    public String getValidAccessToken() {
        validateConfiguration();
        CachedToken current = cachedToken;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (current != null && current.expiresAt().isAfter(now.plusSeconds(megatixProperties.getTokenRefreshSkewSeconds()))) {
            return current.accessToken();
        }

        synchronized (this) {
            current = cachedToken;
            now = OffsetDateTime.now(ZoneOffset.UTC);
            if (current != null && current.expiresAt().isAfter(now.plusSeconds(megatixProperties.getTokenRefreshSkewSeconds()))) {
                return current.accessToken();
            }
            cachedToken = login();
            return cachedToken.accessToken();
        }
    }

    @Override
    public void evictAccessToken() {
        cachedToken = null;
    }

    private CachedToken login() {
        try {
            MegatixLoginResponseDto response = megatixRestClient.post()
                    .uri("/api/v3/accounts/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MegatixLoginRequestDto(megatixProperties.getEmail(), megatixProperties.getPassword()))
                    .retrieve()
                    .body(MegatixLoginResponseDto.class);

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new MegatixClientException("Megatix login returned no access token");
            }

            long expiresIn = response.getExpiresIn() != null ? response.getExpiresIn() : 300L;
            return new CachedToken(response.getAccessToken(), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expiresIn));
        } catch (RestClientResponseException e) {
            throw new MegatixClientException("Megatix login failed", e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new MegatixClientException("Megatix login failed", e);
        }
    }

    private void validateConfiguration() {
        if (isBlank(megatixProperties.getBaseUrl())) {
            throw new MegatixConfigurationException("Megatix base URL is not configured");
        }
        if (isBlank(megatixProperties.getEmail())) {
            throw new MegatixConfigurationException("Megatix email is not configured");
        }
        if (isBlank(megatixProperties.getPassword())) {
            throw new MegatixConfigurationException("Megatix password is not configured");
        }
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String accessToken, OffsetDateTime expiresAt) {
    }
}
