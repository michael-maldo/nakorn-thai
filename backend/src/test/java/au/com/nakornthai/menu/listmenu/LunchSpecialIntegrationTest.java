package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.*;
import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.menu.configuremenu.*;
import au.com.nakornthai.ordering.createorder.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="ONLINE_ORDERING_ENABLED=true") @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class LunchSpecialIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
    }
    @Autowired JdbcTemplate jdbc; @Autowired EntityManager em; @Autowired ListMenuHandler menu;
    @Autowired MenuConfigurationHandler admin; @Autowired CreateOrderHandler orders; @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    final UUID lunch=UUID.fromString("8ed50da5-f6d9-54b3-9611-2bc33b7e54d2");
    final UUID l1=UUID.fromString("2f02a1b7-3d12-5aa6-a039-6d22670e2c2a");
    final UUID l4=UUID.fromString("f5c980c7-5273-527b-a7e2-6c7911c1d764");
    void time(String time) { when(clock.instant()).thenReturn(LocalDateTime.parse("2026-09-07T"+time).atZone(ZoneId.of("Australia/Melbourne")).toInstant()); }
    @BeforeEach void hours() {
        jdbc.update("DELETE FROM restaurant_opening_hours");jdbc.update("DELETE FROM restaurant_closed_date");
        jdbc.update("UPDATE restaurant_settings SET timezone='Australia/Melbourne' WHERE id=1");
        jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,1,'11:30','22:00')",UUID.randomUUID());
        time("13:00:00");
    }
    @Test void v22SeedIsDiscoverableIsolatedAndRelationallyComplete() throws Exception {
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('21','22') AND success",Integer.class));
        mvc.perform(get("/api/menu/collections")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.slug == 'lunch-special')].availability.available").value(true));
        var result=menu.handle(new ListMenuQuery("lunch-special"));assertEquals(lunch,result.id());assertEquals(10,result.items().size());assertEquals(1,result.categories().size());
        assertEquals("Lunch Special",result.categories().getFirst().name());
        for(int i=0;i<10;i++) {
            var item=result.items().get(i);assertTrue(item.name().startsWith("L"+(i+1)+". "));
            assertEquals(1,item.variations().size());assertEquals(1490,item.variations().getFirst().priceMinor());assertTrue(item.variations().getFirst().defaultVariation());
            assertEquals(1,item.optionGroups().size());assertEquals("SINGLE",item.optionGroups().getFirst().selectionType());
            assertEquals(1,item.optionGroups().getFirst().minSelections());assertEquals(1,item.optionGroups().getFirst().maxSelections());
            assertEquals(i==3?2:7,item.optionGroups().getFirst().options().size());
        }
        assertEquals("Rice noodles with egg, tofu, bean sprouts and crushed",result.items().getFirst().description());
        assertEquals(l1,result.items().getFirst().id());assertEquals(l4,result.items().get(3).id());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM menu_collection_schedule WHERE collection_id=?",Integer.class,lunch));
        assertEquals(10,jdbc.queryForObject("SELECT count(*) FROM menu_collection_item WHERE collection_id=? AND collection_category_id='050b2cba-9229-574f-9981-6b100e3ba84d' AND price_override_minor IS NULL",Integer.class,lunch));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM menu_item_dietary_tag t JOIN menu_collection_item m ON m.menu_item_id=t.menu_item_id WHERE m.collection_id=? AND t.dietary_tag_id='b814e25a-44b2-5bb7-aa64-79b1f763bdee'",Integer.class,lunch));
        var main=menu.handle(new ListMenuQuery("main-menu"));assertEquals(82,main.items().size());assertEquals(13,main.categories().size());
        assertTrue(main.items().stream().noneMatch(i->result.items().stream().anyMatch(l->l.id().equals(i.id()))));
        assertEquals(15,main.items().stream().filter(i->!i.optionGroups().isEmpty()).count());
    }
    @Test void publicCutoffAndClosedDatesUseRestaurantTimezoneEvenWhenCollectionZoneDiffers() throws Exception {
        jdbc.update("UPDATE menu_collection SET timezone='UTC' WHERE id=?",lunch);em.clear();
        time("14:29:00");assertTrue(menu.handle(new ListMenuQuery("lunch-special")).availability().available());
        time("14:30:00");assertFalse(menu.handle(new ListMenuQuery("lunch-special")).availability().available());
        time("14:31:00");mvc.perform(get("/api/menu/collections/lunch-special/items")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10)).andExpect(jsonPath("$.availability.reason").value("AFTER_CUTOFF"));
        time("13:00:00");jdbc.update("INSERT INTO restaurant_closed_date(id,closed_date) VALUES (?,'2026-09-07')",UUID.randomUUID());em.clear();
        assertEquals("RESTAURANT_CLOSED",menu.handle(new ListMenuQuery("lunch-special")).availability().reason());
    }
    @Test void seededOptionsUseAuthoritativePricingAndNarrowL4Choices() {
        var item=em.find(MenuItemJpaEntity.class,l1);var group=item.getOptionGroups().getFirst().getOptionGroup();
        var prices=Map.of("beef",1490,"chicken",1490,"veg-tofu",1490,"prawns",2090,"seafood",2290,"crispy-pork",1990,"fish",2090);
        for(var option:group.getOptions()) assertEquals(prices.get(option.getCode()).longValue(),MenuPricing.calculate(1490,true,null,MenuCatalogRules.groups(item),List.of(new MenuPricing.Selection(option.getId(),1))).unitPrice());
        var rice=em.find(MenuItemJpaEntity.class,l4);
        assertEquals(Set.of("chicken","beef"),rice.getOptionGroups().getFirst().getOptionGroup().getOptions().stream().map(MenuOptionJpaEntity::getCode).collect(java.util.stream.Collectors.toSet()));
        for(var option:group.getOptions()) if(Set.of("prawns","veg-tofu").contains(option.getCode()))
            assertThrows(IllegalArgumentException.class,()->MenuPricing.calculate(1490,true,null,MenuCatalogRules.groups(rice),List.of(new MenuPricing.Selection(option.getId(),1))));
    }
    @Test void seededOrderStoresSnapshotsAndSuccessfulReplaySurvivesCutoff() {
        var request=new CreateOrderRequest(UUID.randomUUID(),"a".repeat(64),"Lunch Test","0400000000","",
                List.of(new CreateOrderRequest.Line(UUID.fromString("6e78deed-b9bc-537a-8a08-fc7e0eafbf62"),2,2090,lunch,
                        List.of(new CreateOrderRequest.SelectedOption(UUID.fromString("a9938cdf-241d-5a0d-8009-42f7c5cc385f"),1)))));
        time("14:29:00");var response=orders.handle(request);assertEquals(4180,response.totalMinor());assertEquals("lunch-special",response.items().getFirst().collectionSlug());
        assertEquals(1490L,response.items().getFirst().variationBasePriceMinor());assertNull(response.items().getFirst().collectionPriceOverrideMinor());
        assertEquals("Prawns",response.items().getFirst().selectedOptions().getFirst().optionName());
        em.flush();em.clear();time("14:31:00");assertEquals(response,orders.handle(request));
    }
    @Test void cutoffRoundTripsThroughNormalAdminWithVersionCheck() {
        var before=admin.collections().stream().filter(c->lunch.equals(c.collection().id())).findFirst().orElseThrow().collection();
        var data=(MenuConfigurationRequest.Collection)before.data();assertEquals(LocalTime.of(14,30),data.dailyCutoffTime());
        var request=new MenuConfigurationRequest.Collection(data.name(),data.slug(),data.description(),data.status(),data.active(),data.timezone(),data.startsAt(),data.endsAt(),data.displayOrder(),before.version(),LocalTime.of(14,0));
        var saved=admin.saveCollection(lunch,request);em.clear();assertEquals(LocalTime.of(14,0),em.find(MenuCollectionJpaEntity.class,lunch).getDailyCutoffTime());
        assertEquals(before.version()+1,saved.version());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->admin.saveCollection(lunch,request));
    }
}
