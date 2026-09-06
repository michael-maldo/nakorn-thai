package au.com.nakornthai.ordering.createorder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreateOrderHandlerTest {
    CreateOrderRequest request(List<CreateOrderRequest.Line> lines) {
        return new CreateOrderRequest(UUID.randomUUID(),"a".repeat(64),"Test","0400000000","",lines);
    }
    @Test void fingerprintNormalizesOptionsAndLinesButRetainsCollectionQuantityAndPrice() {
        var c=UUID.randomUUID(); var v=UUID.randomUUID();
        var a=new CreateOrderRequest.SelectedOption(UUID.randomUUID(),1);
        var b=new CreateOrderRequest.SelectedOption(UUID.randomUUID(),2);
        var first=new CreateOrderRequest.Line(v,2,1234,c,List.of(a,b));
        var reordered=new CreateOrderRequest.Line(v,2,1234,c,List.of(b,a));
        assertEquals(CreateOrderHandler.fingerprintLines(request(List.of(first))),CreateOrderHandler.fingerprintLines(request(List.of(reordered))));
        var otherCollection=new CreateOrderRequest.Line(v,2,1234,UUID.randomUUID(),List.of(a,b));
        assertNotEquals(first.configurationKey(),otherCollection.configurationKey());
        assertEquals(CreateOrderHandler.fingerprintLines(request(List.of(first,otherCollection))),CreateOrderHandler.fingerprintLines(request(List.of(otherCollection,first))));
        assertNotEquals(first.configurationKey(),new CreateOrderRequest.Line(v,2,1234,c,List.of(a)).configurationKey());
    }
    @Test void legacyFingerprintRepresentationIsPreserved() {
        var v=UUID.randomUUID();
        assertEquals("[Line[variationId="+v+", quantity=2, expectedUnitPriceMinor=2490]]",
                CreateOrderHandler.fingerprintLines(request(List.of(new CreateOrderRequest.Line(v,2,2490)))));
    }

    @Test void checkoutPersistsAuthoritativeSnapshotsAndReplayBypassesChangedCatalog() throws Exception {
        var em=org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var collection=identified(new au.com.nakornthai.menu.infrastructure.MenuCollectionJpaEntity());
        collection.setName("Lunch"); collection.setSlug("lunch"); collection.setStatus("PUBLISHED");
        var category=identified(new au.com.nakornthai.menu.infrastructure.MenuCategoryJpaEntity()); category.setActive(true);
        var item=identified(new au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity());
        item.setCategory(category); item.setName("Curry"); item.setStatus("PUBLISHED");
        var variation=identified(new au.com.nakornthai.menu.infrastructure.MenuItemVariationJpaEntity());
        variation.setMenuItem(item); variation.setName("Standard"); variation.setPriceMinor(2490L); variation.setDefaultVariation(true);
        var membership=new au.com.nakornthai.menu.infrastructure.MenuCollectionItemJpaEntity();
        membership.setCollection(collection); membership.setMenuItem(item); membership.setPriceOverrideMinor(2000L);
        var group=identified(new au.com.nakornthai.menu.infrastructure.MenuOptionGroupJpaEntity());
        group.setName("Protein"); group.setSelectionType("MULTIPLE");
        var option=identified(new au.com.nakornthai.menu.infrastructure.MenuOptionJpaEntity());
        option.setOptionGroup(group); option.setName("Prawns"); option.setPriceDeltaMinor(600); group.getOptions().add(option);
        var assignment=new au.com.nakornthai.menu.infrastructure.MenuItemOptionGroupJpaEntity();
        assignment.setMenuItem(item); assignment.setOptionGroup(group); assignment.setMinSelections(2); assignment.setMaxSelections(3);
        item.getOptionGroups().add(assignment);
        org.mockito.Mockito.when(em.find(au.com.nakornthai.menu.infrastructure.MenuItemVariationJpaEntity.class,variation.getId())).thenReturn(variation);
        org.mockito.Mockito.when(em.find(au.com.nakornthai.menu.infrastructure.MenuCollectionItemJpaEntity.class,
                new au.com.nakornthai.menu.infrastructure.MenuAssociationId(collection.getId(),item.getId()))).thenReturn(membership);
        org.mockito.Mockito.doAnswer(invocation -> {
            if(invocation.getArgument(0) instanceof au.com.nakornthai.ordering.infrastructure.OrderJpaEntity order) order.setVersion(0L);
            return null;
        }).when(em).persist(org.mockito.ArgumentMatchers.any());
        var handler=new CreateOrderHandler(em,new au.com.nakornthai.ordering.infrastructure.OrderMapper(),true);
        var request=request(List.of(new CreateOrderRequest.Line(variation.getId(),2,3200,collection.getId(),
                List.of(new CreateOrderRequest.SelectedOption(option.getId(),2)))));
        var response=handler.handle(request);
        assertEquals(6400,response.totalMinor());
        var line=response.items().getFirst(); assertEquals(1,line.snapshotVersion());
        assertEquals(collection.getId(),line.collectionId()); assertEquals("Lunch",line.collectionName()); assertEquals("lunch",line.collectionSlug());
        assertEquals(2490L,line.variationBasePriceMinor()); assertEquals(2000L,line.collectionPriceOverrideMinor());
        assertEquals(3200,line.unitPriceMinor()); assertEquals(2,line.selectedOptions().getFirst().quantity());
        assertEquals("Protein",line.selectedOptions().getFirst().optionGroupName());
        var captured=org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(em,org.mockito.Mockito.times(2)).persist(captured.capture());
        var stored=(au.com.nakornthai.ordering.infrastructure.OrderJpaEntity)captured.getAllValues().getFirst();
        assertSame(stored.getItems().getFirst(),stored.getItems().getFirst().getSelectedOptions().getFirst().getOrderItem());
        org.mockito.Mockito.when(em.find(au.com.nakornthai.ordering.infrastructure.OrderJpaEntity.class,request.requestId())).thenReturn(stored);
        collection.setActive(false); option.setName("Changed"); option.setPriceDeltaMinor(999);
        assertEquals(response,new CreateOrderHandler(em,new au.com.nakornthai.ordering.infrastructure.OrderMapper(),false).handle(request));
        org.mockito.Mockito.verify(em,org.mockito.Mockito.times(2)).persist(org.mockito.ArgumentMatchers.any());
    }
    private <T extends au.com.nakornthai.menu.infrastructure.MenuUuidJpaEntity> T identified(T entity) throws Exception {
        var id=au.com.nakornthai.menu.infrastructure.MenuUuidJpaEntity.class.getDeclaredField("id");
        id.setAccessible(true); id.set(entity,UUID.randomUUID()); return entity;
    }
}
