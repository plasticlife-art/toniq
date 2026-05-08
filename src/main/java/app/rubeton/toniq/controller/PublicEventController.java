package app.rubeton.toniq.controller;

import app.rubeton.toniq.service.PublicEventQueryService;
import app.rubeton.toniq.service.publicweb.PublicEventDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final PublicEventQueryService publicEventQueryService;

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
}
