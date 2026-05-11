package app.rubeton.toniq.controller;

import app.rubeton.toniq.service.AvailabilityRefreshService;
import app.rubeton.toniq.service.EventRefreshInProgressException;
import app.rubeton.toniq.service.PublicEventQueryService;
import app.rubeton.toniq.service.publicweb.PublicEventDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final PublicEventQueryService publicEventQueryService;
    private final AvailabilityRefreshService availabilityRefreshService;

    @GetMapping("/by-slug/{slug}")
    public PublicEventDetailResponse getBySlug(@PathVariable("slug") final String slug) {
        return publicEventQueryService.findPublishedEventBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{megatixEventId}")
    public PublicEventDetailResponse getByMegatixId(@PathVariable("megatixEventId") final String megatixEventId) {
        return publicEventQueryService.findPublishedEventByMegatixId(megatixEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{megatixEventId}/availability/refresh")
    public PublicEventDetailResponse refreshAvailability(@PathVariable("megatixEventId") final String megatixEventId) {
        PublicEventDetailResponse current = publicEventQueryService.findPublishedEventByMegatixId(megatixEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            availabilityRefreshService.refreshAvailability(megatixEventId);
            return publicEventQueryService.findPublishedEventByMegatixId(megatixEventId)
                    .orElse(current);
        } catch (EventRefreshInProgressException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to refresh availability", e);
        }
    }
}
