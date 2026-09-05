package au.com.nakornthai.ordering.createorder;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="ONLINE_ORDERING_ENABLED=true")
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class CreateOrderIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    UUID item, variation, id;
    String token="a".repeat(64);
    @BeforeEach void fixture() {
        item=UUID.randomUUID(); variation=UUID.randomUUID(); id=UUID.randomUUID();
        var category=UUID.randomUUID(); var collection=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'Order test',?)",category,"order-"+category);
        jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Test curry',?,'Fresh curry','PUBLISHED')",item,category,"order-"+item);
        jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Standard',2490,true)",variation,item);
        jdbc.update("INSERT INTO menu_collection(id,name,slug,status) VALUES (?,'Order test',?,'PUBLISHED')",collection,"order-"+collection);
        jdbc.update("INSERT INTO menu_collection_item(collection_id,menu_item_id) VALUES (?,?)",collection,item);
    }
    String payload(long price) {
        return """
            {"requestId":"%s","trackingToken":"%s","customerName":"Test Customer","phone":"0400000000","notes":"No cutlery",
            "items":[{"variationId":"%s","quantity":2,"expectedUnitPriceMinor":%d}]}
            """.formatted(id,token,variation,price);
    }
    void create() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(payload(2490)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalMinor").value(4980));
    }
    long version() { return jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id); }
    void transition(String role,String next,boolean paid) throws Exception {
        mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user(role).roles(role)).with(csrf())
                .contentType("application/json").content("""
                {"version":%d,"status":"%s","pickupMinutes":20,"paymentCollected":%s}
                """.formatted(version(),next,paid))).andExpect(status().isNoContent());
    }
    @Test void fullPickupLifecycleWithPrivateTrackingAndRoleQueues() throws Exception {
        create(); create();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
        mvc.perform(get("/api/orders/"+id).header("X-Order-Token",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").doesNotExist()).andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(get("/api/orders/"+id).header("X-Order-Token","b".repeat(64))).andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/foh/orders").with(user("front").roles("FOH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '"+id+"')].phone").value("0400000000"));
        mvc.perform(get("/api/staff/kitchen/orders").with(user("cook").roles("BOH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '"+id+"')]").isEmpty());
        transition("FOH","ACCEPTED",false);
        mvc.perform(get("/api/staff/kitchen/orders").with(user("cook").roles("BOH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '"+id+"')].phone").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        transition("BOH","PREPARING",false); transition("BOH","READY",false); transition("FOH","COMPLETED",true);
        mvc.perform(get("/api/orders/"+id).header("X-Order-Token",token)).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.paidAt").isNotEmpty());
        assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM restaurant_order_event WHERE order_id=?",Integer.class,id));
    }
    @Test void serverRejectsChangedPriceAndUnavailableDish() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(payload(1))).andExpect(status().isConflict());
        jdbc.update("UPDATE menu_item SET is_available=false WHERE id=?",item); em.clear();
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(payload(2490))).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
    }
    @Test void originalPricesAndNamesRemainAfterMenuEdits() throws Exception {
        create();
        jdbc.update("UPDATE menu_item_variation SET price_minor=3000 WHERE id=?",variation);
        jdbc.update("UPDATE menu_item SET name='New recipe name' WHERE id=?",item); em.clear();
        mvc.perform(get("/api/orders/"+id).header("X-Order-Token",token)).andExpect(jsonPath("$.items[0].dishName").value("Test curry"))
                .andExpect(jsonPath("$.items[0].unitPriceMinor").value(2490));
        create(); // Same attempt remains idempotent despite menu edits.
    }
    @Test void cannotReuseCheckoutKeyWithDifferentDetails() throws Exception {
        create();
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(payload(2490).replace("No cutlery","Extra cutlery")))
                .andExpect(status().isConflict());
    }
    @Test void protectedQueuesAndTransitionsRequireRoleAndCsrf() throws Exception {
        mvc.perform(get("/api/staff/foh/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/foh/orders").with(user("cook").roles("BOH"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").contentType("application/json").content(payload(2490))).andExpect(status().isForbidden());
        create();
        mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("cook").roles("BOH")).with(csrf())
                .contentType("application/json").content("{\"version\":0,\"status\":\"ACCEPTED\",\"pickupMinutes\":20,\"paymentCollected\":false}"))
                .andExpect(status().isForbidden());
    }
    @Test void invalidTransitionsStaleEditsAndUnpaidCompletionAreRejected() throws Exception {
        create();
        mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("front").roles("FOH")).with(csrf())
                .contentType("application/json").content("{\"version\":0,\"status\":\"COMPLETED\",\"paymentCollected\":true}"))
                .andExpect(status().isConflict());
        transition("FOH","ACCEPTED",false);
        mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("cook").roles("BOH")).with(csrf())
                .contentType("application/json").content("{\"version\":0,\"status\":\"PREPARING\",\"paymentCollected\":false}"))
                .andExpect(status().isConflict());
        transition("BOH","PREPARING",false); transition("BOH","READY",false);
        mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("front").roles("FOH")).with(csrf())
                .contentType("application/json").content("{\"version\":"+version()+",\"status\":\"COMPLETED\",\"paymentCollected\":false}"))
                .andExpect(status().isBadRequest());
    }
}
