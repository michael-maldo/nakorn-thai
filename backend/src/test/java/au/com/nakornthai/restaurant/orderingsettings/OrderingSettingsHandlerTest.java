package au.com.nakornthai.restaurant.orderingsettings;

import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderingSettingsHandlerTest {
    final JpaRestaurantRepository repository = mock(JpaRestaurantRepository.class);
    final EntityManager em = mock(EntityManager.class);
    final RestaurantAvailabilityService availability = mock(RestaurantAvailabilityService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-25T06:00:00Z"), ZoneOffset.UTC);
    final RestaurantSettingsJpaEntity settings = new RestaurantSettingsJpaEntity();
    OrderingSettingsHandler handler(boolean configured) { return new OrderingSettingsHandler(repository, em, availability, clock, configured); }
    @BeforeEach void setup() throws Exception {
        var version = RestaurantAuditJpaEntity.class.getDeclaredField("version"); version.setAccessible(true); version.set(settings, 3L);
        when(repository.settings(any())).thenReturn(settings);
        when(availability.isOpen(clock.instant())).thenReturn(true);
    }
    @Test void configurationPauseAndHoursAreIndependentAndHaveClearReasons() {
        settings.setOrderingPaused(true);
        assertEquals("DISABLED_BY_CONFIGURATION", handler(false).status().reason());
        assertThrows(ResponseStatusException.class, () -> handler(false).requireAcceptingOrders());
        assertEquals("PAUSED_BY_STAFF", handler(true).status().reason());
        assertThrows(ResponseStatusException.class, () -> handler(true).requireAcceptingOrders());
        settings.setOrderingPaused(false);
        when(availability.isOpen(clock.instant())).thenReturn(false);
        assertEquals("OUTSIDE_OPENING_HOURS", handler(true).status().reason());
        when(availability.isOpen(clock.instant())).thenReturn(true);
        assertTrue(handler(true).status().enabled());
        assertDoesNotThrow(() -> handler(true).requireAcceptingOrders());
    }
    @Test void saveTrimsCustomerMessageAndDoesNotOverrideConfigurationOrHours() {
        var result = handler(false).save(new OrderingSettingsHandler.Update(false, "  Kitchen is busy.  ", 3L));
        assertFalse(result.acceptingOrders()); assertEquals("Kitchen is busy.", result.pauseMessage());
        assertEquals("Kitchen is busy.", handler(true).status().message());
        assertFalse(result.status().enabled());
        verify(repository).settings(LockModeType.PESSIMISTIC_WRITE); verify(em).flush();
        when(availability.isOpen(clock.instant())).thenReturn(false);
        result = handler(true).save(new OrderingSettingsHandler.Update(true, "  ", 3L));
        assertTrue(result.acceptingOrders()); assertNull(result.pauseMessage());
        assertEquals("OUTSIDE_OPENING_HOURS", result.status().reason());
        assertFalse(handler(false).status().enabled());
    }
    @Test void staleVersionCannotOverwriteAnotherStaffChange() {
        var error = assertThrows(ResponseStatusException.class,
                () -> handler(true).save(new OrderingSettingsHandler.Update(false, "Busy", 2L)));
        assertEquals(409, error.getStatusCode().value());
        assertFalse(settings.isOrderingPaused()); verify(em, never()).flush();
    }
}
