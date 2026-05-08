package app.rubeton.toniq.service;

import app.rubeton.toniq.service.publicweb.PublicEventDetailResponse;

import java.util.Optional;

public interface PublicEventQueryService {

    Optional<PublicEventDetailResponse> findPublishedEventBySlug(String slug);

    Optional<PublicEventDetailResponse> findPublishedEventByMegatixId(String megatixEventId);
}
