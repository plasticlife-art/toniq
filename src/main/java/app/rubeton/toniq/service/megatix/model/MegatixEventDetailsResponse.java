package app.rubeton.toniq.service.megatix.model;

import app.rubeton.toniq.service.megatix.dto.MegatixEventDetailsDto;

public record MegatixEventDetailsResponse(MegatixEventDetailsDto payload, String rawPayloadJson) {
}
