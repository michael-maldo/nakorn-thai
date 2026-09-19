package au.com.nakornthai.menu.configuremenu;

import au.com.nakornthai.menu.infrastructure.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuConfigurationHandlerTest {
    EntityManager em=mock(EntityManager.class);
    au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService restaurant=mock(au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService.class);
    Instant now=Instant.parse("2026-09-21T13:00:00Z");
    MenuConfigurationHandler handler=new MenuConfigurationHandler(em,restaurant,Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void catalogLock() {
        Query query=mock(Query.class); when(em.createNativeQuery(anyString(),eq(Object.class))).thenReturn(query);
    }
    @Test void listIncludesDraftsAndSeparatesCatalogFromRestaurantAvailability() {
        var c=new MenuCollectionJpaEntity(); c.setName("Menu"); c.setStatus("PUBLISHED"); c.setTimezone("UTC");
        var draft=new MenuCollectionJpaEntity(); draft.setName("Draft");
        TypedQuery<MenuCollectionJpaEntity> collections=mock(TypedQuery.class);
        TypedQuery<MenuCollectionItemJpaEntity> memberships=mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(MenuCollectionJpaEntity.class))).thenReturn(collections);
        when(collections.getResultList()).thenReturn(java.util.List.of(c,draft));
        when(em.createQuery(anyString(),eq(MenuCollectionItemJpaEntity.class))).thenReturn(memberships);
        when(memberships.setParameter(eq("id"),any())).thenReturn(memberships);
        when(memberships.getResultList()).thenReturn(java.util.List.of());
        when(restaurant.schedule()).thenReturn(new au.com.nakornthai.restaurant.domain.RestaurantSchedule(ZoneId.of("Australia/Melbourne"),java.util.List.of(),java.util.Set.of()));
        var rows=handler.collections();
        assertEquals(2,rows.size()); assertTrue(rows.getFirst().availability().available());
        assertFalse(rows.getFirst().orderingAvailability().available());
        assertEquals("RESTAURANT_CLOSED",rows.getFirst().orderingAvailability().reason());
        assertEquals("Australia/Melbourne",rows.getFirst().restaurantTimezone());
        assertEquals(now,rows.getFirst().availability().evaluatedAt());
        assertEquals("NOT_PUBLISHED",rows.get(1).availability().reason());
    }
    @Test void createAndUpdateMetadataPreservesCutoffAndIndependentTimezone() throws Exception {
        var create=new MenuConfigurationRequest.Collection("Seasonal","seasonal","  ","DRAFT",false,"UTC",null,null,5,null,LocalTime.of(14,30));
        handler.saveCollection(null,create);
        var captor=org.mockito.ArgumentCaptor.forClass(MenuCollectionJpaEntity.class); verify(em).persist(captor.capture());
        var c=captor.getValue(); assertNull(c.getDescription()); assertEquals("DRAFT",c.getStatus()); assertFalse(c.isActive());
        assertEquals(LocalTime.of(14,30),c.getDailyCutoffTime()); assertEquals("UTC",c.getTimezone());
        var version=MenuAuditJpaEntity.class.getDeclaredField("version"); version.setAccessible(true); version.set(c,2L);
        var id=UUID.randomUUID(); when(em.find(MenuCollectionJpaEntity.class,id)).thenReturn(c);
        var request=new MenuConfigurationRequest.Collection("Updated","updated","Description","PUBLISHED",true,"UTC",now,now.plusSeconds(3600),7,2L,null);
        handler.saveCollection(id,request);
        assertEquals("Updated",c.getName()); assertEquals("updated",c.getSlug()); assertEquals("Description",c.getDescription());
        assertEquals("PUBLISHED",c.getStatus()); assertTrue(c.isActive()); assertEquals(7,c.getDisplayOrder());
        assertEquals(now,c.getStartsAt()); assertEquals(now.plusSeconds(3600),c.getEndsAt()); assertNull(c.getDailyCutoffTime());
    }
    @Test void rejectsInvalidTimezoneAndInstantRange() {
        var badZone=new MenuConfigurationRequest.Collection("Menu","menu",null,"PUBLISHED",true,"invalid/zone",null,null,0,null,null);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.saveCollection(null,badZone)).getStatusCode().value());
        var now=Instant.now();
        var badRange=new MenuConfigurationRequest.Collection("Menu","menu",null,"PUBLISHED",true,"Australia/Melbourne",now,now,0,null,null);
        assertThrows(ResponseStatusException.class,()->handler.saveCollection(null,badRange));
        verify(em,never()).persist(any());
    }
    @Test void rejectsEqualTimesPartialTimesAndWrongDateShape() {
        var id=UUID.randomUUID(); when(em.find(MenuCollectionJpaEntity.class,id)).thenReturn(new MenuCollectionJpaEntity());
        for(var request:java.util.List.of(
                new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.NOON,LocalTime.NOON,true,0,null),
                new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.NOON,null,true,0,null),
                new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.NOON.withNano(1),LocalTime.of(14,0),true,0,null),
                new MenuConfigurationRequest.Schedule("SPECIFIC_DATE",(short)1,LocalDate.now(),null,null,true,0,null)))
            assertThrows(ResponseStatusException.class,()->handler.saveSchedule(id,null,request));
        verify(em,never()).persist(any());
    }
    @Test void existingResourcesRequireMatchingVersion() throws Exception {
        var id=UUID.randomUUID(); var collection=new MenuCollectionJpaEntity();
        var version=MenuAuditJpaEntity.class.getDeclaredField("version"); version.setAccessible(true); version.set(collection,3L);
        when(em.find(MenuCollectionJpaEntity.class,id)).thenReturn(collection);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.archiveCollection(id,null)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->handler.archiveCollection(id,2L)).getStatusCode().value());
        handler.archiveCollection(id,3L); assertEquals("ARCHIVED",collection.getStatus());
    }
    @Test void membershipChangesOnlyTheRequestedAssociationAndRemovalKeepsItem() throws Exception {
        var collectionId=UUID.randomUUID(); var itemId=UUID.randomUUID(); var placementId=UUID.randomUUID();
        var c=new MenuCollectionJpaEntity();
        var identifier=MenuUuidJpaEntity.class.getDeclaredField("id"); identifier.setAccessible(true); identifier.set(c,collectionId);
        var item=new MenuItemJpaEntity(); identifier.set(item,itemId);
        var canonical=new MenuCategoryJpaEntity(); item.setCategory(canonical);
        var category=new MenuCategoryJpaEntity();
        var placement=new MenuCollectionCategoryJpaEntity(); placement.setCollection(c); placement.setCategory(category); identifier.set(placement,placementId);
        when(em.find(MenuCollectionJpaEntity.class,collectionId)).thenReturn(c);
        when(em.find(MenuItemJpaEntity.class,itemId)).thenReturn(item);
        when(em.find(MenuCollectionCategoryJpaEntity.class,placementId)).thenReturn(placement);
        handler.saveMembership(collectionId,itemId,new MenuConfigurationRequest.Membership(placementId,0L,8,null));
        var captor=org.mockito.ArgumentCaptor.forClass(MenuCollectionItemJpaEntity.class); verify(em).persist(captor.capture());
        var m=captor.getValue(); assertSame(item,m.getMenuItem()); assertSame(c,m.getCollection());
        assertSame(category,m.effectiveCategory()); assertEquals(0L,m.getPriceOverrideMinor()); assertEquals(8,m.getDisplayOrder());
        var version=MenuAuditJpaEntity.class.getDeclaredField("version"); version.setAccessible(true); version.set(m,1L);
        when(em.find(eq(MenuCollectionItemJpaEntity.class),any())).thenReturn(m);
        handler.saveMembership(collectionId,itemId,new MenuConfigurationRequest.Membership(null,null,3,1L));
        assertSame(canonical,m.effectiveCategory()); assertNull(m.getPriceOverrideMinor());
        handler.deleteMembership(collectionId,itemId,1L); verify(em).remove(m); verify(em,never()).remove(item);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->handler.saveMembership(collectionId,itemId,new MenuConfigurationRequest.Membership(null,null,0,0L))).getStatusCode().value());
        identifier.set(c,UUID.randomUUID());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.saveMembership(collectionId,itemId,new MenuConfigurationRequest.Membership(placementId,null,0,1L))).getStatusCode().value());
    }
    @Test void singleAssignmentCannotAllowMultipleQuantities() {
        var item=UUID.randomUUID(); var group=UUID.randomUUID();
        when(em.find(MenuItemJpaEntity.class,item)).thenReturn(new MenuItemJpaEntity());
        when(em.find(MenuOptionGroupJpaEntity.class,group)).thenReturn(new MenuOptionGroupJpaEntity());
        assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,group,new MenuConfigurationRequest.Assignment(0,2,0,null)));
        verify(em,never()).persist(any());
    }
}
