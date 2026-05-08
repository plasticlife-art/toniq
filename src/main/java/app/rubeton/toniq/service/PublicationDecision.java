package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.PublicationMode;

public record PublicationDecision(
        PublicationMode publicationMode,
        Boolean lastMegatixWebhookEnabled,
        boolean webhookReceived,
        boolean deletedGatePassed,
        boolean sroastGatePassed,
        boolean effectivePublished,
        String blockedReason,
        String webhookHint
) {
}
