package app.rubeton.toniq.service.megatix.mapper;

import app.rubeton.toniq.service.megatix.dto.MegatixWebhookPayloadDto;
import app.rubeton.toniq.service.megatix.model.MegatixWebhookCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MegatixWebhookMapper {

    @Mapping(target = "eventType", source = "payload.event")
    @Mapping(target = "megatixEventId", source = "payload.data.eventId")
    @Mapping(target = "cryptoEnabled", source = "payload.data.enabled")
    @Mapping(target = "rawPayloadJson", expression = "java(rawPayloadJson)")
    MegatixWebhookCommand toCommand(MegatixWebhookPayloadDto payload, String rawPayloadJson);
}
