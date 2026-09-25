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
        return handler.saveCollection(null,new MenuConfigurationRequest.Collection("Menu","menu-"+UUID.randomUUID(),null,"PUBLISHED",true,"Australia/Melbourne",null,null,1,null,null));
    }
    UUID id(MenuConfigurationHandler.Resource r) { return (UUID)r.id(); }
    @Test void collectionMetadataCutoffAndVersionsRoundTrip() {
        var c=collection(); var other=collection();
        Instant start=Instant.parse("2026-09-01T00:00:00Z"),end=start.plusSeconds(86400);
        var saved=handler.saveCollection(id(c),new MenuConfigurationRequest.Collection("Edited","edited-"+UUID.randomUUID(),"Details","ARCHIVED",false,"UTC",start,end,9,c.version(),LocalTime.of(14,30)));
        em.clear();
        var rows=handler.collections();
        var loaded=rows.stream().filter(r -> r.collection().id().equals(c.id())).findFirst().orElseThrow();
        var data=(MenuConfigurationRequest.Collection)loaded.collection().data();
        assertEquals("Edited",data.name()); assertEquals("Details",data.description()); assertEquals("ARCHIVED",data.status());
        assertFalse(data.active()); assertEquals("UTC",data.timezone()); assertEquals(9,data.displayOrder());
        assertEquals(start,data.startsAt()); assertEquals(end,data.endsAt()); assertEquals(LocalTime.of(14,30),data.dailyCutoffTime());
        assertEquals(saved.version(),loaded.collection().version()); assertTrue(saved.version()>c.version());
        var unchanged=(MenuConfigurationRequest.Collection)rows.stream().filter(r -> r.collection().id().equals(other.id())).findFirst().orElseThrow().collection().data();
        assertEquals("PUBLISHED",unchanged.status()); assertTrue(unchanged.active()); assertNull(unchanged.dailyCutoffTime());
        assertEquals("NOT_PUBLISHED",loaded.availability().reason());
    }
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
        assertEquals(1,jdbc.queryForObject("select count(*) from menu_item where id=?",Integer.class,item));
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
        var option=handler.saveOption(id(group),null,new MenuConfigurationRequest.Option("prawns","Prawns",true,0,null));
        var assignment=handler.saveAssignment(item,id(group),new MenuConfigurationRequest.Assignment(1,1,0,null,((MenuConfigurationRequest.Group)handler.groups().stream().filter(g -> g.group().id().equals(group.id())).findFirst().orElseThrow().group().data()).version(),java.util.List.of(new MenuConfigurationRequest.OptionPrice(id(option),600))));
        em.clear();
        assertEquals(600,menu.handle(new ListMenuQuery(slug)).items().getFirst().optionGroups().getFirst().options().getFirst().priceDeltaMinor());
        handler.deactivateOption(id(group),id(option),option.version()); em.flush(); em.clear();
        assertFalse(menu.handle(new ListMenuQuery(slug)).items().getFirst().available());
        handler.deleteAssignment(item,id(group),assignment.version()); em.flush(); em.clear();
        assertTrue(menu.handle(new ListMenuQuery(slug)).items().getFirst().optionGroups().isEmpty());
    }
    @Test void reusableChoicesHaveIndependentItemPricesAndNewChoicesAreNotSilentlyFree() {
        var created=handler.createAssignedGroup(item,new MenuConfigurationRequest.CreateAssignedGroup("protein-"+UUID.randomUUID(),"Protein","SINGLE",1,0,
                java.util.List.of(new MenuConfigurationRequest.NewChoice("beef","Beef",200),new MenuConfigurationRequest.NewChoice("pork","Pork",0))));
        UUID groupId=(UUID)created.id();
        var firstData=(MenuConfigurationRequest.Assignment)created.data();
        UUID beef=handler.groups().stream().filter(g -> g.group().id().equals(groupId)).findFirst().orElseThrow().options().stream()
                .filter(o -> ((MenuConfigurationRequest.Option)o.data()).code().equals("beef")).map(this::id).findFirst().orElseThrow();
        UUID second=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Second dish',?,'Test','PUBLISHED')",second,category,"item-"+second);
        var secondAssignment=handler.saveAssignment(second,groupId,new MenuConfigurationRequest.Assignment(1,1,0,null,firstData.groupVersion(),java.util.List.of(new MenuConfigurationRequest.OptionPrice(beef,300))));
        var edited=handler.saveAssignment(item,groupId,new MenuConfigurationRequest.Assignment(1,1,0,created.version(),firstData.groupVersion(),java.util.List.of(new MenuConfigurationRequest.OptionPrice(beef,250))));
        assertTrue(edited.version()>created.version());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,groupId,
                new MenuConfigurationRequest.Assignment(1,1,0,created.version(),firstData.groupVersion(),firstData.prices()))).getStatusCode().value());
        em.flush(); em.clear();
        var firstItem=em.find(au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity.class,item);
        var secondItem=em.find(au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity.class,second);
        var firstGroups=au.com.nakornthai.menu.infrastructure.MenuCatalogRules.groups(firstItem);
        var secondGroups=au.com.nakornthai.menu.infrastructure.MenuCatalogRules.groups(secondItem);
        var selection=java.util.List.of(new au.com.nakornthai.menu.domain.MenuPricing.Selection(beef,1));
        assertEquals(1250,au.com.nakornthai.menu.domain.MenuPricing.calculate(1000,true,null,firstGroups,selection).unitPrice());
        assertEquals(1300,au.com.nakornthai.menu.domain.MenuPricing.calculate(1000,true,null,secondGroups,selection).unitPrice());
        var c=collection(); handler.saveMembership(id(c),item,new MenuConfigurationRequest.Membership(null,null,0,null));
        em.flush(); em.clear();
        var dish=menu.handle(new ListMenuQuery(((MenuConfigurationRequest.Collection)c.data()).slug())).items().getFirst();
        assertEquals(250,dish.optionGroups().getFirst().options().stream().filter(o -> o.id().equals(beef)).findFirst().orElseThrow().priceDeltaMinor());
        var added=handler.saveOption(groupId,null,new MenuConfigurationRequest.Option("chicken","Chicken",true,2,null));
        em.flush(); em.clear();
        var newOption=au.com.nakornthai.menu.infrastructure.MenuCatalogRules.groups(em.find(au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity.class,item))
                .getFirst().options().stream().filter(o -> o.id().equals(added.id())).findFirst().orElseThrow();
        assertFalse(newOption.active());
        assertThrows(IllegalArgumentException.class,()->au.com.nakornthai.menu.domain.MenuPricing.calculate(1000,true,null,
                au.com.nakornthai.menu.infrastructure.MenuCatalogRules.groups(em.find(au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity.class,item)),
                java.util.List.of(new au.com.nakornthai.menu.domain.MenuPricing.Selection(id(added),1))));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,groupId,
                new MenuConfigurationRequest.Assignment(1,1,0,edited.version(),firstData.groupVersion(),firstData.prices()))).getStatusCode().value());
        handler.deleteAssignment(second,groupId,secondAssignment.version()); em.flush(); em.clear();
        assertEquals(0,jdbc.queryForObject("select count(*) from menu_item_option_price where menu_item_id=?",Integer.class,second));
        assertEquals(1,jdbc.queryForObject("select count(*) from menu_option_group where id=?",Integer.class,groupId));
        assertEquals(250L,jdbc.queryForObject("select price_delta_minor from menu_item_option_price where menu_item_id=? and option_id=?",Long.class,item,beef));
    }
    @Test void addingChoiceToUninitializedSharedGroupAppearsOnce() {
        var group=handler.saveGroup(null,new MenuConfigurationRequest.Group("spice-"+UUID.randomUUID(),"Spice","SINGLE",true,null));
        em.flush(); em.clear();
        handler.saveOption(id(group),null,new MenuConfigurationRequest.Option("mild","Mild",true,0,null));
        assertEquals(1,handler.groups().stream().filter(g -> g.group().id().equals(group.id())).findFirst().orElseThrow().options().size());
    }
    @Test void assignmentRejectsForeignDuplicateNegativePricesAndRequiredWithoutChoices() {
        var group=handler.saveGroup(null,new MenuConfigurationRequest.Group("extras-"+UUID.randomUUID(),"Extras","MULTIPLE",true,null));
        var option=handler.saveOption(id(group),null,new MenuConfigurationRequest.Option("rice","Rice",true,0,null));
        Long groupVersion=handler.groups().stream().filter(g -> g.group().id().equals(group.id())).findFirst().orElseThrow().group().version();
        for(var prices:java.util.List.of(
                java.util.List.of(new MenuConfigurationRequest.OptionPrice(UUID.randomUUID(),100)),
                java.util.List.of(new MenuConfigurationRequest.OptionPrice(id(option),100),new MenuConfigurationRequest.OptionPrice(id(option),200)),
                java.util.List.of(new MenuConfigurationRequest.OptionPrice(id(option),-1)))) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,id(group),new MenuConfigurationRequest.Assignment(0,3,0,null,groupVersion,prices))).getStatusCode().value());
        }
        assertEquals(400,assertThrows(ResponseStatusException.class,()->handler.saveAssignment(item,id(group),new MenuConfigurationRequest.Assignment(1,3,0,null,groupVersion,java.util.List.of()))).getStatusCode().value());
        handler.saveAssignment(item,id(group),new MenuConfigurationRequest.Assignment(0,3,0,null,groupVersion,java.util.List.of(new MenuConfigurationRequest.OptionPrice(id(option),150))));
        em.flush(); em.clear();
        var groups=au.com.nakornthai.menu.infrastructure.MenuCatalogRules.groups(em.find(au.com.nakornthai.menu.infrastructure.MenuItemJpaEntity.class,item));
        assertEquals(1000,au.com.nakornthai.menu.domain.MenuPricing.calculate(1000,true,null,groups,java.util.List.of()).unitPrice());
        assertEquals(1300,au.com.nakornthai.menu.domain.MenuPricing.calculate(1000,true,null,groups,java.util.List.of(new au.com.nakornthai.menu.domain.MenuPricing.Selection(id(option),2))).unitPrice());
    }
    @Test void v23CopiesEveryExistingAssignmentPriceBeforeRemovingGlobalPrices() throws Exception {
        // Exercise the exact migration in an isolated transactional schema.
        String schema="option_migration_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE SCHEMA "+schema);
        jdbc.execute("SET LOCAL search_path TO "+schema);
        jdbc.execute("CREATE TABLE menu_option (id UUID PRIMARY KEY, option_group_id UUID NOT NULL, price_delta_minor BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE menu_item_option_group (menu_item_id UUID NOT NULL, option_group_id UUID NOT NULL, PRIMARY KEY(menu_item_id,option_group_id))");
        UUID group=UUID.randomUUID(),option=UUID.randomUUID(),second=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_option VALUES (?,?,600)",option,group);
        jdbc.update("INSERT INTO menu_item_option_group VALUES (?,?),(?,?)",item,group,second,group);
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/migration/V23__price_options_per_menu_item.sql"));
        assertEquals(java.util.List.of(600L,600L),jdbc.queryForList("SELECT price_delta_minor FROM menu_item_option_price",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='menu_option' AND column_name='price_delta_minor'",Integer.class,schema));
        jdbc.update("UPDATE menu_item_option_price SET price_delta_minor=300 WHERE menu_item_id=?",second);
        assertEquals(600L,jdbc.queryForObject("SELECT price_delta_minor FROM menu_item_option_price WHERE menu_item_id=?",Long.class,item));
    }
    @Test void rejectsCrossCollectionPlacementAndStaleEdits() {
        var a=collection(); var b=collection();
        var placement=handler.saveCategory(id(a),null,new MenuConfigurationRequest.Category(category,0,null));
        assertThrows(ResponseStatusException.class,()->handler.saveMembership(id(b),item,new MenuConfigurationRequest.Membership(id(placement),null,0,null)));
        assertThrows(ResponseStatusException.class,()->handler.archiveCollection(id(a),a.version()+1));
    }
}
