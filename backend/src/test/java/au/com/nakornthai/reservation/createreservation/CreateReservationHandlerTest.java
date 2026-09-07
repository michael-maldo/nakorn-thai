package au.com.nakornthai.reservation.createreservation;

import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.domain.*;
import au.com.nakornthai.reservation.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateReservationHandlerTest {
    final SpringDataReservationRepository reservations = mock(SpringDataReservationRepository.class);
    final EntityManager em = mock(EntityManager.class, RETURNS_DEEP_STUBS);
    final RestaurantAvailabilityService availability = mock(RestaurantAvailabilityService.class);
    final Clock clock = mock(Clock.class);
    final Instant operationInstant = Instant.parse("2026-09-07T01:00:00Z");
    final CreateReservationHandler handler = new CreateReservationHandler(reservations, em, availability, clock);
    final CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), "Guest", "0400000000", 4,
            LocalDateTime.parse("2026-09-07T18:00:00"), "");
    RestaurantSchedule schedule(boolean active, LocalDate... closures) {
        return new RestaurantSchedule(ZoneId.of("Australia/Melbourne"),
                List.of(new OpeningHours(1, LocalTime.of(17,0), LocalTime.of(22,0), active)), Set.of(closures));
    }
    @BeforeEach void setup() { when(clock.instant()).thenReturn(operationInstant); }
    @Test void closedRequestedTimeRejectsWithoutSaving() {
        when(availability.schedule()).thenReturn(schedule(false));
        assertThrows(RestaurantClosedException.class, () -> handler.handle(request));
        verify(reservations, never()).saveAndFlush(any());
    }
    @Test void closedDateOverridesOpenBookingWindow() {
        when(availability.schedule()).thenReturn(schedule(true, LocalDate.of(2026,9,7)));
        assertThrows(RestaurantClosedException.class, () -> handler.handle(request));
        verify(reservations, never()).saveAndFlush(any());
    }
    @Test void requestedOpenTimeProceedsEvenWhenOperationTimeIsClosedAndReplayBypassesSchedule() {
        when(availability.schedule()).thenReturn(schedule(true));
        var result = handler.handle(request);
        assertEquals(request.requestId(), result.get("reference"));
        var capture = org.mockito.ArgumentCaptor.forClass(ReservationJpaEntity.class);
        verify(reservations).saveAndFlush(capture.capture());
        var stored = capture.getValue();
        assertEquals(request.requestedAt(), stored.getRequestedAt());
        assertEquals(operationInstant, stored.getCreatedAt()); assertEquals(operationInstant, stored.getUpdatedAt());
        when(reservations.findById(request.requestId())).thenReturn(Optional.of(stored));
        when(availability.schedule()).thenReturn(schedule(false));
        assertEquals(result, handler.handle(request));
        verify(availability, times(1)).schedule(); verify(clock, times(1)).instant();
    }
    @Test void existingFutureTimeRuleRemains() {
        when(availability.schedule()).thenReturn(schedule(true));
        when(clock.instant()).thenReturn(Instant.parse("2026-09-08T01:00:00Z"));
        var error = assertThrows(ResponseStatusException.class, () -> handler.handle(request));
        assertEquals(400, error.getStatusCode().value()); verify(reservations, never()).saveAndFlush(any());
    }
    @Test void futureValidationUsesConfiguredTimezone() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-07T07:00:00Z"));
        when(availability.schedule()).thenReturn(new RestaurantSchedule(ZoneId.of("Pacific/Auckland"),
                List.of(new OpeningHours(1,LocalTime.of(17,0),LocalTime.of(22,0),true)),Set.of()));
        // Requested 18:00 is past in Auckland (19:00), but future in Melbourne (17:00).
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.handle(request)).getStatusCode().value());
        verify(reservations, never()).saveAndFlush(any());
    }
    @Test void bookingUsesConfiguredTimezoneRatherThanHardcodedMelbourne() {
        // Monday 18:00 Auckland is 06:00 UTC, inside this window; server is UTC.
        when(availability.schedule()).thenReturn(new RestaurantSchedule(ZoneId.of("Pacific/Auckland"),
                List.of(new OpeningHours(1,LocalTime.of(18,0),LocalTime.of(19,0),true)),Set.of()));
        assertEquals(request.requestId(), handler.handle(request).get("reference"));
    }
}
