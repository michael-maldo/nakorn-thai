package au.com.nakornthai.restaurant.orderingsettings;

import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;

@Service
public class OrderingSettingsHandler {
    private final JpaRestaurantRepository restaurant;
    private final EntityManager em;
    private final RestaurantAvailabilityService availability;
    private final Clock clock;
    private final boolean configured;

    public OrderingSettingsHandler(JpaRestaurantRepository restaurant, EntityManager em,
            RestaurantAvailabilityService availability, Clock clock,
            @Value("${ONLINE_ORDERING_ENABLED:false}") boolean configured) {
        this.restaurant = restaurant; this.em = em; this.availability = availability;
        this.clock = clock; this.configured = configured;
    }
    public record Update(@NotNull Boolean acceptingOrders, @Size(max = 300) String pauseMessage,
                         @NotNull @PositiveOrZero Long version) {}
    public record Status(boolean enabled, String reason, String message) {}
    public record Settings(boolean configured, boolean acceptingOrders, String pauseMessage,
                           boolean restaurantOpen, Long version, Status status) {}

    @Transactional
    public Settings read() { return view(restaurant.settings(LockModeType.PESSIMISTIC_READ)); }

    @Transactional
    public Status status() { return read().status(); }

    @Transactional
    public Settings save(Update request) {
        var settings = restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        if (!request.version().equals(settings.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Restaurant settings changed; reload before saving");
        settings.setOrderingPaused(!request.acceptingOrders());
        settings.setOrderingPauseMessage(request.pauseMessage() == null || request.pauseMessage().isBlank()
                ? null : request.pauseMessage().trim());
        em.flush();
        return view(settings);
    }

    // Called inside the order transaction; the shared settings lock serializes pause with new orders.
    @Transactional
    public void requireAcceptingOrders() {
        if (!configured) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Online ordering is currently unavailable");
        var settings = restaurant.settings(LockModeType.PESSIMISTIC_READ);
        if (settings.isOrderingPaused())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, pauseMessage(settings));
    }

    private Settings view(RestaurantSettingsJpaEntity settings) {
        boolean open = availability.isOpen(clock.instant());
        Status status = !configured ? new Status(false, "DISABLED_BY_CONFIGURATION", "Online ordering is currently unavailable.")
                : settings.isOrderingPaused() ? new Status(false, "PAUSED_BY_STAFF", pauseMessage(settings))
                : !open ? new Status(false, "OUTSIDE_OPENING_HOURS", "Online ordering is closed outside restaurant opening hours.")
                : new Status(true, "AVAILABLE", "Online ordering is available.");
        return new Settings(configured, !settings.isOrderingPaused(), settings.getOrderingPauseMessage(), open, settings.getVersion(), status);
    }
    private String pauseMessage(RestaurantSettingsJpaEntity settings) {
        return settings.getOrderingPauseMessage() == null ? "Online ordering is temporarily paused. Please try again later."
                : settings.getOrderingPauseMessage();
    }
}
