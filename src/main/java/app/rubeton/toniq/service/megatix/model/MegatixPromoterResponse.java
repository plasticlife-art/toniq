package app.rubeton.toniq.service.megatix.model;

import app.rubeton.toniq.service.megatix.dto.MegatixPromoterDto;

public record MegatixPromoterResponse(MegatixPromoterDto payload, String rawPayloadJson) {
}
