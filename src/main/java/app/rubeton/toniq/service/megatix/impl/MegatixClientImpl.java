package app.rubeton.toniq.service.megatix.impl;

import app.rubeton.toniq.config.MegatixProperties;
import app.rubeton.toniq.service.MegatixClient;
import app.rubeton.toniq.service.megatix.MegatixAuthService;
import app.rubeton.toniq.service.megatix.MegatixClientException;
import app.rubeton.toniq.service.megatix.dto.MegatixApiEnvelopeDto;
import app.rubeton.toniq.service.megatix.dto.MegatixEventDetailsDto;
import app.rubeton.toniq.service.megatix.dto.MegatixPromoterDto;
import com.fasterxml.jackson.databind.JsonNode;
import app.rubeton.toniq.service.megatix.model.MegatixEventDetailsResponse;
import app.rubeton.toniq.service.megatix.model.MegatixPromoterResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class MegatixClientImpl implements MegatixClient {

    private final RestClient.Builder restClientBuilder;
    private final MegatixProperties megatixProperties;
    private final MegatixAuthService megatixAuthService;
    private final ObjectMapper objectMapper;

    @Override
    public MegatixEventDetailsResponse fetchEventDetails(final String eventId) {
        String body = executeGetWithAuthRetry("/api/v3/events/{eventId}?is_ota=true", eventId);
        return new MegatixEventDetailsResponse(readPayload(body, MegatixEventDetailsDto.class), body);
    }

    @Override
    public MegatixPromoterResponse fetchEventPromoter(final String eventId) {
        String body = executeGetWithAuthRetry("/api/v3/events/{eventId}/promoter-ota", eventId);
        return new MegatixPromoterResponse(readPayload(body, MegatixPromoterDto.class), body);
    }

    private String executeGetWithAuthRetry(final String uriTemplate, final String eventId) {
        try {
            return executeAuthorizedGet(uriTemplate, eventId);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                megatixAuthService.evictAccessToken();
                try {
                    return executeAuthorizedGet(uriTemplate, eventId);
                } catch (RestClientResponseException retryException) {
                    throw new MegatixClientException("Megatix request failed after re-login",
                            retryException.getStatusCode().value(), retryException);
                }
            }
            throw new MegatixClientException("Megatix request failed", status, e);
        } catch (RuntimeException e) {
            throw new MegatixClientException("Megatix request failed", e);
        }
    }

    private String executeAuthorizedGet(final String uriTemplate, final String eventId) {
        RestClient client = restClientBuilder.baseUrl(trimTrailingSlash(megatixProperties.getBaseUrl())).build();
        return client.get()
                .uri(uriTemplate, eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + megatixAuthService.getValidAccessToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    private <T> T readPayload(final String body, final Class<T> type) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode payloadNode = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            return objectMapper.treeToValue(payloadNode, type);
        } catch (JsonProcessingException e) {
            throw new MegatixClientException("Failed to parse Megatix response", e);
        }
    }

    private String trimTrailingSlash(final String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
