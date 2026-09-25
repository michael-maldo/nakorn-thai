package au.com.nakornthai.ordering.createorder;

import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LunchSpecialOrderTest {
    final EntityManager em=mock(EntityManager.class,RETURNS_DEEP_STUBS);
    final RestaurantAvailabilityService availability=mock(RestaurantAvailabilityService.class);
    final Clock clock=mock(Clock.class);
    final CreateOrderHandler handler=new CreateOrderHandler(em,new OrderMapper(),true,availability,clock);
    MenuCollectionJpaEntity collection; MenuItemVariationJpaEntity variation; MenuOptionJpaEntity prawns;
    RestaurantSchedule restaurant=new RestaurantSchedule(ZoneId.of("Australia/Melbourne"),
            List.of(new OpeningHours(1,LocalTime.of(11,30),LocalTime.of(22,0),true)),Set.of());
    Instant at(String time) { return LocalDateTime.parse("2026-09-07T"+time).atZone(restaurant.timezone()).toInstant(); }
    @BeforeEach void fixture() throws Exception {
        collection=identified(new MenuCollectionJpaEntity());collection.setName("Lunch Special");collection.setSlug("lunch-special");
        collection.setStatus("PUBLISHED");collection.setDailyCutoffTime(LocalTime.of(14,30));collection.setTimezone("UTC");
        var category=identified(new MenuCategoryJpaEntity());category.setActive(true);
        var item=identified(new MenuItemJpaEntity());item.setCategory(category);item.setName("L1. Pad Thai");item.setStatus("PUBLISHED");
        variation=identified(new MenuItemVariationJpaEntity());variation.setMenuItem(item);variation.setName("Standard");variation.setPriceMinor(1490L);variation.setDefaultVariation(true);
        var membership=new MenuCollectionItemJpaEntity();membership.setCollection(collection);membership.setMenuItem(item);
        var group=identified(new MenuOptionGroupJpaEntity());group.setName("Protein");group.setSelectionType("SINGLE");
        prawns=identified(new MenuOptionJpaEntity());prawns.setOptionGroup(group);prawns.setName("Prawns");group.getOptions().add(prawns);
        var assignment=new MenuItemOptionGroupJpaEntity();assignment.setMenuItem(item);assignment.setOptionGroup(group);assignment.setMinSelections(1);assignment.setMaxSelections(1);assignment.getOptionPrices().put(prawns.getId(),600L);item.getOptionGroups().add(assignment);
        when(em.find(MenuItemVariationJpaEntity.class,variation.getId())).thenReturn(variation);
        when(em.find(MenuCollectionItemJpaEntity.class,new MenuAssociationId(collection.getId(),item.getId()))).thenReturn(membership);
        when(availability.schedule()).thenReturn(restaurant);when(clock.instant()).thenReturn(at("14:29:00"));
        doAnswer(invocation->{if(invocation.getArgument(0) instanceof OrderJpaEntity order)order.setVersion(0L);return null;}).when(em).persist(any());
    }
    CreateOrderRequest request(long expected,List<CreateOrderRequest.SelectedOption> options) {
        return new CreateOrderRequest(UUID.randomUUID(),"a".repeat(64),"Guest","0400000000","",
                List.of(new CreateOrderRequest.Line(variation.getId(),2,expected,collection.getId(),options)));
    }
    CreateOrderRequest request() { return request(2090,List.of(new CreateOrderRequest.SelectedOption(prawns.getId(),1))); }
    @Test void loadedBeforeCutoffDoesNotAuthorizeANewOrderAtOrAfterCutoff() {
        assertTrue(MenuCatalogRules.availability(collection,at("14:25:00"),restaurant).available());
        for(var time:List.of("14:30:00","14:31:00")) {
            when(clock.instant()).thenReturn(at(time));
            var failure=assertThrows(ResponseStatusException.class,()->handler.handle(request()));
            assertEquals(409,failure.getStatusCode().value());assertEquals("Selected collection is currently unavailable",failure.getReason());
        }
        verify(em,never()).persist(any());
    }
    @Test void successfulOrderSnapshotsLunchAndReplayBypassesCutoffClosureAndPriceChanges() {
        var request=request();var response=handler.handle(request);var line=response.items().getFirst();
        assertEquals(4180,response.totalMinor());assertEquals(2090,line.unitPriceMinor());
        assertEquals(collection.getId(),line.collectionId());assertEquals("Lunch Special",line.collectionName());assertEquals("lunch-special",line.collectionSlug());
        assertEquals(1490L,line.variationBasePriceMinor());assertNull(line.collectionPriceOverrideMinor());
        assertEquals("Prawns",line.selectedOptions().getFirst().optionName());assertEquals(600,line.selectedOptions().getFirst().priceDeltaMinor());assertEquals(1,line.selectedOptions().getFirst().quantity());
        var captured=org.mockito.ArgumentCaptor.forClass(Object.class);verify(em,times(2)).persist(captured.capture());
        var stored=(OrderJpaEntity)captured.getAllValues().getFirst();assertEquals(at("14:29:00"),stored.getCreatedAt());
        when(em.find(OrderJpaEntity.class,request.requestId())).thenReturn(stored);
        when(clock.instant()).thenReturn(at("14:31:00"));
        when(availability.schedule()).thenReturn(new RestaurantSchedule(restaurant.timezone(),List.of(),Set.of()));
        variation.setPriceMinor(9999L);variation.getMenuItem().getOptionGroups().getFirst().getOptionPrices().put(prawns.getId(),9999L);collection.setActive(false);
        assertEquals(response,handler.handle(request));
        verify(clock,times(1)).instant();verify(availability,times(1)).schedule();verify(em,times(2)).persist(any());
    }
    @Test void invalidOptionsAndStalePricesAreRejectedBeforePersistence() {
        for(var options:List.of(List.<CreateOrderRequest.SelectedOption>of(),List.of(new CreateOrderRequest.SelectedOption(prawns.getId(),2)),
                List.of(new CreateOrderRequest.SelectedOption(UUID.randomUUID(),1))))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.handle(request(2090,options))).getStatusCode().value());
        var stale=assertThrows(ResponseStatusException.class,()->handler.handle(request(1490,List.of(new CreateOrderRequest.SelectedOption(prawns.getId(),1)))));
        assertEquals(409,stale.getStatusCode().value());assertTrue(stale.getReason().contains("price changed"));verify(em,never()).persist(any());
    }
    @Test void restaurantClosureStillUsesPhase4Error() {
        when(availability.schedule()).thenReturn(new RestaurantSchedule(restaurant.timezone(),restaurant.hours(),Set.of(LocalDate.of(2026,9,7))));
        assertThrows(RestaurantClosedException.class,()->handler.handle(request()));verify(em,never()).persist(any());
    }
    private <T extends MenuUuidJpaEntity> T identified(T entity) throws Exception {
        var field=MenuUuidJpaEntity.class.getDeclaredField("id");field.setAccessible(true);field.set(entity,UUID.randomUUID());return entity;
    }
}
