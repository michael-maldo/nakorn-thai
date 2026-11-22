package au.com.nakornthai.restaurant.googlereviews;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class GoogleReviewsController {
    static final String INTEGRATION = "/api/staff/restaurant/integrations/google";
    static final String REVIEWS = "/api/staff/restaurant/google-reviews";
    private final GoogleReviewsService service;

    GoogleReviewsController(GoogleReviewsService service) { this.service = service; }

    @GetMapping(INTEGRATION + "/status")
    ResponseEntity<GoogleReviewsService.Status> status(Authentication staff) { return response(service.status(session(staff))); }

    @PostMapping(INTEGRATION + "/connect")
    ResponseEntity<Map<String, String>> connect(Authentication staff) {
        return response(Map.of("authorizationUrl", service.connect(session(staff))));
    }

    @PostMapping(INTEGRATION + "/complete")
    ResponseEntity<GoogleReviewsService.Status> complete(Authentication staff) {
        return response(service.finish(session(staff)));
    }

    @GetMapping(value = "/api/integrations/google/callback", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code) {
        boolean accepted = service.callback(state, code);
        return ResponseEntity.status(accepted ? HttpStatus.OK : HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
                .body("<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                        + "<title>StaffPlatform</title><main><h1>"
                        + (accepted ? "Google authorization received" : "Google authorization could not be completed")
                        + "</h1><p>Return to StaffPlatform to continue.</p></main></html>");
    }

    @GetMapping(INTEGRATION + "/locations")
    ResponseEntity<Map<String, List<GoogleBusinessClient.Location>>> locations() {
        return response(Map.of("locations", service.locations()));
    }

    record LocationUpdate(String resource) {}

    @PutMapping(INTEGRATION + "/location")
    ResponseEntity<GoogleReviewsService.Status> location(@RequestBody LocationUpdate location, Authentication staff) {
        service.location(location.resource());
        return response(service.status(session(staff)));
    }

    @PostMapping(INTEGRATION + "/disconnect")
    ResponseEntity<Map<String, Boolean>> disconnect() { return response(Map.of("revokedAtGoogle", service.disconnect())); }

    @GetMapping(REVIEWS)
    ResponseEntity<GoogleReviewsService.Page> reviews(@RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String publication, @RequestParam(defaultValue = "0") int stars,
            @RequestParam(defaultValue = "newest") String sort, @RequestParam(defaultValue = "0") int page) {
        if (query.length() > 200 || stars < 0 || stars > 5 || page < 0 || page > 10000
                || !List.of("all", "featured", "unfeatured").contains(publication)
                || !List.of("newest", "oldest", "highest", "lowest").contains(sort))
            throw GoogleReviewsService.error(HttpStatus.BAD_REQUEST, "Invalid review filter.");
        return response(service.reviews(query, publication, stars, sort, page));
    }

    record Selection(Boolean featured) {}

    @PatchMapping(REVIEWS + "/{id}/publication")
    ResponseEntity<Map<String, Boolean>> publication(@PathVariable String id, @RequestBody Selection selection, Authentication staff) {
        if (!id.matches("[A-Za-z0-9_-]{43}") || selection.featured() == null)
            throw GoogleReviewsService.error(HttpStatus.BAD_REQUEST, "Invalid publication selection.");
        service.publication(id, selection.featured(), staff.getName());
        return response(Map.of("featured", selection.featured()));
    }

    @GetMapping(REVIEWS + "/display-settings")
    ResponseEntity<GoogleReviewsService.Settings> settings() { return response(service.settings()); }

    @PutMapping(REVIEWS + "/display-settings")
    ResponseEntity<GoogleReviewsService.Settings> settings(@RequestBody GoogleReviewsService.Settings settings) {
        return response(service.save(settings));
    }

    @GetMapping(REVIEWS + "/preview")
    ResponseEntity<GoogleReviewsService.PublicFeed> preview() { return response(service.feed(true)); }

    @PostMapping(REVIEWS + "/sync")
    ResponseEntity<GoogleReviewsService.Status> sync(Authentication staff) {
        service.requestSync();
        return response(service.status(session(staff)));
    }

    @GetMapping("/api/public/google-reviews")
    ResponseEntity<GoogleReviewsService.PublicFeed> publicFeed() { return response(service.feed(false)); }

    private static UUID session(Authentication staff) {
        if (staff == null || !(staff.getDetails() instanceof UUID id))
            throw GoogleReviewsService.error(HttpStatus.UNAUTHORIZED, "An active staff session is required.");
        return id;
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
