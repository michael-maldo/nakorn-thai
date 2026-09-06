package au.com.nakornthai.menu.configuremenu;

import au.com.nakornthai.menu.listmenu.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class MenuConfigurationIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
    }
    @Autowired MenuConfigurationHandler handler;
    @Autowired ListMenuHandler menu;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    UUID category,item,variation;
    @BeforeEach void fixture() {
        category=UUID.randomUUID(); item=UUID.randomUUID(); variation=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'Canonical',?)",category,"category-"+category);
        jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Dish',?,'Test','PUBLISHED')",item,category,"item-"+item);
        jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Standard',2490,true)",variation,item);
    }
    MenuConfigurationHandler.Resource collection() {
        return handler.saveCollection(null,new MenuConfigurationRequest.Collection("Menu","menu-"+UUID.randomUUID(),null,"PUBLISHED",true,"Australia/Melbourne",null,null,1,null));
    }
    UUID id(MenuConfigurationHandler.Resource r) { return (UUID)r.id(); }
    @Test void collectionPlacementOverrideAndLegacyFallbackRoundTrip() {
        var c=collection(); var second=collection();
        UUID alternative=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'Lunch category',?)",alternative,"category-"+alternative);
        var placement=handler.saveCategory(id(c),null,new MenuConfigurationRequest.Category(alternative,2,null));
        var membership=handler.saveMembership(id(c),item,new MenuConfigurationRequest.Membership(id(placement),0L,3,null));
        handler.saveMembership(id(second),item,new MenuConfigurationRequest.Membership(null,null,4,null));
        em.clear();
        var slug=((MenuConfigurationRequest.Collection)c.data()).slug();
        var dish=menu.handle(new ListMenuQuery(slug)).items().getFirst();
        assertEquals(alternative,dish.category().id()); assertEquals(0,dish.variations().getFirst().priceMinor());
        assertEquals(2490,dish.variations().getFirst().variationBasePriceMinor());
        var fallback=menu.handle(new ListMenuQuery(((MenuConfigurationRequest.Collection)second.data()).slug())).items().getFirst();
        assertEquals(category,fallback.category().id()); assertEquals(2490,fallback.variations().getFirst().priceMinor());
        handler.deleteMembership(id(c),item,membership.version()); em.flush(); em.clear();
        assertTrue(menu.handle(new ListMenuQuery(slug)).items().isEmpty());
        assertFalse(menu.handle(new ListMenuQuery(((MenuConfigurationRequest.Collection)second.data()).slug())).items().isEmpty());
    }
    @Test void schedulesAndOptionsCanBeCreatedUpdatedAndRemovedOrDeactivated() {
        var c=collection(); handler.saveMembership(id(c),item,new MenuConfigurationRequest.Membership(null,null,0,null));
        var schedule=handler.saveSchedule(id(c),null,new MenuConfigurationRequest.Schedule("WEEKLY",(short)1,null,LocalTime.of(17,0),LocalTime.of(1,0),false,0,null));
        em.clear();
        var slug=((MenuConfigurationRequest.Collection)c.data()).slug();
        assertFalse(menu.handle(new ListMenuQuery(slug)).availability().available());
        handler.deleteSchedule(id(c),id(schedule),schedule.version()); em.flush(); em.clear();
        assertTrue(menu.handle(new ListMenuQuery(slug)).availability().available());
        var group=handler.saveGroup(null,new MenuConfigurationRequest.Group("protein-"+UUID.randomUUID(),"Protein","SINGLE",true,null));
        var option=handler.saveOption(id(group),null,new MenuConfigurationRequest.Option("prawns","Prawns",600,true,0,null));
        var assignment=handler.saveAssignment(item,id(group),new MenuConfigurationRequest.Assignment(1,1,0,null));
        em.clear();
        assertEquals(600,menu.handle(new ListMenuQuery(slug)).items().getFirst().optionGroups().getFirst().options().getFirst().priceDeltaMinor());
        handler.deactivateOption(id(group),id(option),option.version()); em.flush(); em.clear();
        assertFalse(menu.handle(new ListMenuQuery(slug)).items().getFirst().available());
        handler.deleteAssignment(item,id(group),assignment.version()); em.flush(); em.clear();
        assertTrue(menu.handle(new ListMenuQuery(slug)).items().getFirst().optionGroups().isEmpty());
    }
    @Test void rejectsCrossCollectionPlacementAndStaleEdits() {
        var a=collection(); var b=collection();
        var placement=handler.saveCategory(id(a),null,new MenuConfigurationRequest.Category(category,0,null));
        assertThrows(ResponseStatusException.class,()->handler.saveMembership(id(b),item,new MenuConfigurationRequest.Membership(id(placement),null,0,null)));
        assertThrows(ResponseStatusException.class,()->handler.archiveCollection(id(a),a.version()+1));
    }
}
