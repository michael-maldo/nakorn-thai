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
    MenuConfigurationHandler handler=new MenuConfigurationHandler(em);
    @BeforeEach void catalogLock() {
        Query query=mock(Query.class); when(em.createNativeQuery(anyString(),eq(Object.class))).thenReturn(query);
    }
    @Test void rejectsInvalidTimezoneAndInstantRange() {
        var badZone=new MenuConfigurationRequest.Collection("Menu","menu",null,"PUBLISHED",true,"invalid/zone",null,null,0,null);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.saveCollection(null,badZone)).getStatusCode().value());
        var now=Instant.now();
        var badRange=new MenuConfigurationRequest.Collection("Menu","menu",null,"PUBLISHED",true,"Australia/Melbourne",now,now,0,null);
        assertThrows(ResponseStatusException.class,()->handler.saveCollection(null,badRange));
        verify(em,never()).persist(any());
    }
    @Test void rejectsEqualTimesPartialTimesAndWrongDateShape() {
        var id=UUID.randomUUID(); when(em.find(MenuCollectionJpaEntity.class,id)).thenReturn(new MenuCollectionJpaEntity());
        for(var request:java.util.List.of(
                new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.NOON,LocalTime.NOON,true,0,null),
                new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.NOON,null,true,0,null),
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
    @Test void singleAssignmentCannotAllowMultipleQuantities() {
        var item=UUID.randomUUID(); var group=UUID.randomUUID();
        when(em.find(MenuItemJpaEntity.class,item)).thenReturn(new MenuItemJpaEntity());
        when(em.find(MenuOptionGroupJpaEntity.class,group)).thenReturn(new MenuOptionGroupJpaEntity());
        assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,group,new MenuConfigurationRequest.Assignment(0,2,0,null)));
        verify(em,never()).persist(any());
    }
}
