package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LunchSpecialRepositoryTest {
    @Test void discoveryAndItemReadShareCutoffSemanticsAndKeepUnavailableLunchVisible() throws Exception {
        var collections=mock(SpringDataMenuCollectionRepository.class);
        var memberships=mock(SpringDataMenuCollectionItemRepository.class);
        var restaurant=mock(RestaurantAvailabilityService.class);var clock=mock(Clock.class);
        var repository=new JpaMenuItemRepository(collections,memberships,new MenuItemMapper("/media/menu/"),restaurant,clock);
        var lunch=new MenuCollectionJpaEntity();lunch.setName("Lunch Special");lunch.setSlug("lunch-special");lunch.setStatus("PUBLISHED");lunch.setDailyCutoffTime(LocalTime.of(14,30));lunch.setTimezone("UTC");
        var id=MenuUuidJpaEntity.class.getDeclaredField("id");id.setAccessible(true);id.set(lunch,UUID.fromString("8ed50da5-f6d9-54b3-9611-2bc33b7e54d2"));
        when(collections.findByStatusOrderByDisplayOrderAscIdAsc("PUBLISHED")).thenReturn(List.of(lunch));
        when(collections.findVisibleBySlug("lunch-special")).thenReturn(Optional.of(lunch));
        when(restaurant.schedule()).thenReturn(new RestaurantSchedule(ZoneId.of("Australia/Melbourne"),List.of(new OpeningHours(1,LocalTime.of(11,30),LocalTime.of(22,0),true)),Set.of()));
        when(clock.instant()).thenReturn(Instant.parse("2026-09-07T04:29:00Z"));
        var before=repository.findPublishedCollections();assertEquals("lunch-special",before.getFirst().slug());assertTrue(before.getFirst().availability().available());
        when(clock.instant()).thenReturn(Instant.parse("2026-09-07T04:30:00Z"));
        var after=repository.findPublishedCollections();assertEquals(1,after.size());assertEquals("AFTER_CUTOFF",after.getFirst().availability().reason());
        var menu=repository.findVisibleCollection("lunch-special").orElseThrow();assertEquals(after.getFirst().availability(),menu.availability());
        verify(clock,times(3)).instant();verify(restaurant,times(3)).schedule();
    }
}
