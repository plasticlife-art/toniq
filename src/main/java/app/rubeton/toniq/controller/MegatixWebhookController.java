package app.rubeton.toniq.controller;

import app.rubeton.toniq.config.MegatixProperties;
import app.rubeton.toniq.service.megatix.MegatixSyncCoordinator;
import app.rubeton.toniq.service.megatix.dto.MegatixWebhookPayloadDto;
import app.rubeton.toniq.service.megatix.mapper.MegatixWebhookMapper;
import app.rubeton.toniq.service.megatix.model.MegatixWebhookCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/megatix")
@RequiredArgsConstructor
public class MegatixWebhookController {

    private static final String SUPPORTED_EVENT_TYPE = "event.ota.status.updated";

    private final ObjectMapper objectMapper;
    private final MegatixProperties megatixProperties;
    private final MegatixWebhookMapper megatixWebhookMapper;
    private final MegatixSyncCoordinator megatixSyncCoordinator;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(name = "x-signature", required = false) final String signature,
            @RequestBody final String rawPayload) {
        if (signature == null || signature.isBlank() || !signature.equals(megatixProperties.getWebhookSignature())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        MegatixWebhookPayloadDto payload;
        try {
            payload = objectMapper.readValue(rawPayload, MegatixWebhookPayloadDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "invalid_json"));
        }

        MegatixWebhookCommand command = megatixWebhookMapper.toCommand(payload, rawPayload);
        if (command.getEventType() == null || command.getEventType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_event_type"));
        }
        if (command.getMegatixEventId() == null || command.getMegatixEventId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_event_id"));
        }
        if (command.getCryptoEnabled() == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_enabled"));
        }
        if (!SUPPORTED_EVENT_TYPE.equals(command.getEventType())) {
            megatixSyncCoordinator.recordUnsupportedWebhook(command);
            return ResponseEntity.accepted().body(Map.of("status", "ignored"));
        }

        megatixSyncCoordinator.submitWebhook(command);
        return ResponseEntity.accepted().body(Map.of("status", "accepted"));
    }
}
