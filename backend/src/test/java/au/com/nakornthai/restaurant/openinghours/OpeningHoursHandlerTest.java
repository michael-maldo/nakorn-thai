package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpeningHoursHandlerTest {
    final JpaRestaurantRepository restaurant = mock(JpaRestaurantRepository.class);
    final EntityManager em = mock(EntityManager.class);
    final OpeningHoursHandler handler = new OpeningHoursHandler(restaurant, em);
    @Test void invalidTimezoneCannotBeSaved() {
        var error = assertThrows(ResponseStatusException.class, () -> handler.saveSettings(new OpeningHoursRequest.Settings("Bad/Timezone", 0L)));
        assertEquals(400, error.getStatusCode().value()); verifyNoInteractions(em);
    }
    @Test void staleTimezoneAndWindowChangesAreRejected() {
        var settings = mock(RestaurantSettingsJpaEntity.class); when(settings.getVersion()).thenReturn(2L);
        when(restaurant.settings(LockModeType.PESSIMISTIC_WRITE)).thenReturn(settings);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> handler.saveSettings(new OpeningHoursRequest.Settings("Australia/Melbourne", 1L))).getStatusCode().value());
        var id = UUID.randomUUID(); var window = mock(OpeningHoursJpaEntity.class); when(window.getVersion()).thenReturn(3L);
        when(em.find(OpeningHoursJpaEntity.class,id)).thenReturn(window);
        assertThrows(ResponseStatusException.class, () -> handler.saveWindow(id,new OpeningHoursRequest.Window((short)1,LocalTime.NOON,LocalTime.MIDNIGHT,true,0,2L)));
        assertThrows(ResponseStatusException.class, () -> handler.deleteWindow(id,2L));
        verify(em, never()).remove(any()); verify(em, never()).flush();
    }
    @Test void duplicateClosedDateCannotBeSaved() {
        var existing = new ClosedDateJpaEntity(); existing.setId(UUID.randomUUID()); existing.setClosedDate(LocalDate.of(2026,12,25));
        when(restaurant.closedDates()).thenReturn(List.of(existing));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> handler.saveClosure(null,
                new OpeningHoursRequest.Closure(existing.getClosedDate(),"Test closure",null))).getStatusCode().value());
        verify(em, never()).persist(any());
    }
    @Test void equalOpeningAndClosingTimesAreRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> handler.saveWindow(null,
                new OpeningHoursRequest.Window((short)1,LocalTime.NOON,LocalTime.NOON,true,0,null))).getStatusCode().value());
        verify(em, never()).persist(any());
    }
}
