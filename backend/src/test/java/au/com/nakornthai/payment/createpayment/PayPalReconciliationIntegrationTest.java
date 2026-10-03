package au.com.nakornthai.payment.createpayment;

import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately no test @Transactional: fixtures commit, and each MockMvc request
// enters the real handler proxy in its own transaction, on a separate connection.
@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=3"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class PayPalReconciliationIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
  p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
  p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
 }
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired EntityManager em;
 @Autowired ObjectMapper json;
 @MockitoBean PayPalPaymentProvider paypal;
 UUID id;
 String providerId,reference;
 final String token="a".repeat(64);

 @BeforeEach void fixture() {
  id=UUID.randomUUID();providerId="PP"+id.toString().replace("-","");reference="CAP"+id.toString().replace("-","");
  jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,payment_method,created_at,updated_at) VALUES (?,?,?,'Test Customer','0400000000','',1990,'PAYPAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,CreateOrderHandler.hash(token),"b".repeat(64));
  jdbc.update("INSERT INTO order_payment(order_id,method,provider_order_id,status,updated_at) VALUES (?,'PAYPAL',?,'PENDING',CURRENT_TIMESTAMP)",id,providerId);
 }
 @AfterEach void cleanup() {
  jdbc.update("DELETE FROM order_payment WHERE order_id=?",id);
  jdbc.update("DELETE FROM restaurant_order WHERE id=?",id);
 }
 JsonNode details(boolean captured) {
  var unit=new HashMap<String,Object>();
  unit.put("custom_id",id.toString());unit.put("amount",Map.of("currency_code","AUD","value","19.90"));
  if(captured)unit.put("payments",Map.of("captures",List.of(Map.of("id",reference,"status","COMPLETED","final_capture",true,"amount",Map.of("currency_code","AUD","value","19.90")))));
  return json.valueToTree(Map.of("id",providerId,"intent","CAPTURE","status",captured?"COMPLETED":"APPROVED","purchase_units",List.of(unit)));
 }
 JsonNode check() throws Exception {
  var result=mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isOk()).andReturn();
  return json.readTree(result.getResponse().getContentAsString());
 }
 void assertPaidWithoutDuplicates() {
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_payment WHERE order_id=?",Integer.class,id));
  assertNotNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
  assertEquals("PAID",jdbc.queryForObject("SELECT status FROM order_payment WHERE order_id=?",String.class,id));
  assertEquals(reference,jdbc.queryForObject("SELECT confirmation_reference FROM order_payment WHERE order_id=?",String.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_payment WHERE confirmation_reference=?",Integer.class,reference));
 }

 @Test void concurrentChecksSerializeOnPostgresOrderLockAndCaptureOnce() throws Exception {
  var captured=new AtomicBoolean();
  var captures=new AtomicInteger();
  var blockerPid=new AtomicInteger();
  var captureEntered=new CountDownLatch(1);
  var releaseCapture=new CountDownLatch(1);
  when(paypal.details(providerId)).thenAnswer(call->details(captured.get()));
  when(paypal.capture(providerId,id)).thenAnswer(call->{
   // EntityManager is bound to this request's actual handler transaction.
   blockerPid.set(((Number)em.createNativeQuery("SELECT pg_backend_pid()",Integer.class).getSingleResult()).intValue());
   captures.incrementAndGet();captureEntered.countDown();
   assertTrue(releaseCapture.await(10,TimeUnit.SECONDS),"Test did not release capture");
   captured.set(true);return details(true);
  });

  var pool=Executors.newFixedThreadPool(2);
  try {
   Future<JsonNode> first=pool.submit(this::check);
   assertTrue(captureEntered.await(10,TimeUnit.SECONDS),"First request never entered capture");
   Future<JsonNode> second=pool.submit(this::check);

   // Third connection observes PostgreSQL, not Mockito timing. The second
   // request must be waiting on the first request's restaurant_order row lock.
   Integer waitingPid=null;
   long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
   while(waitingPid==null && System.nanoTime()<deadline) {
    var waiters=jdbc.queryForList("SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)) AND wait_event_type='Lock' AND query ILIKE '%restaurant_order%'",Integer.class,blockerPid.get());
    if(!waiters.isEmpty())waitingPid=waiters.getFirst();
    else Thread.sleep(25);
   }
   assertNotNull(waitingPid,"Second check did not block on the real PostgreSQL order lock");
   assertNotEquals(blockerPid.get(),waitingPid.intValue(),"Checks must use independent database sessions");
   assertFalse(first.isDone());assertFalse(second.isDone());
   assertEquals(1,captures.get(),"Blocked request must not enter capture");

   releaseCapture.countDown();
   JsonNode firstResult=first.get(10,TimeUnit.SECONDS);
   JsonNode secondResult=second.get(10,TimeUnit.SECONDS);
   assertTrue(firstResult.path("paid").asBoolean());assertTrue(secondResult.path("paid").asBoolean());
   assertEquals("PAID",firstResult.path("status").asText());
   assertEquals(firstResult,secondResult);
   assertEquals(1,captures.get());
   verify(paypal,times(1)).capture(providerId,id);
   assertPaidWithoutDuplicates();
  } finally {
   releaseCapture.countDown();pool.shutdownNow();
   assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS),"Request threads did not finish");
  }
 }

 @Test void lostCaptureResponseRollsBackLocallyThenReconcilesWithoutRecapture() throws Exception {
  var remoteCaptured=new AtomicBoolean();
  var captures=new AtomicInteger();
  when(paypal.details(providerId)).thenAnswer(call->details(remoteCaptured.get()));
  when(paypal.capture(providerId,id)).thenAnswer(call->{
   captures.incrementAndGet();remoteCaptured.set(true);
   // Remote capture succeeded; simulate the adapter's sanitized transport error.
   throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal request could not be completed. Check payment status before retrying.");
  });
  mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token))
   .andExpect(status().isBadGateway());
  assertTrue(remoteCaptured.get());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
  assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM order_payment WHERE order_id=?",String.class,id));
  assertNull(jdbc.queryForObject("SELECT confirmation_reference FROM order_payment WHERE order_id=?",String.class,id));
  assertEquals(providerId,jdbc.queryForObject("SELECT provider_order_id FROM order_payment WHERE order_id=?",String.class,id));

  JsonNode retry=check();
  assertTrue(retry.path("paid").asBoolean());assertEquals("PAID",retry.path("status").asText());
  assertEquals(1,captures.get());verify(paypal,times(1)).capture(providerId,id);
  verify(paypal,times(2)).details(providerId);
  assertPaidWithoutDuplicates();
 }
}
