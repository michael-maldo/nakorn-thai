package au.com.nakornthai.restaurant.configuration;

import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"PAYID_IDENTIFIER=fallback@example.test","PAYID_ACCOUNT_NAME=Fallback Restaurant","PAYPAL_CLIENT_ID=","PAYPAL_CLIENT_SECRET=","PAYPAL_RETURN_URL=https://restaurant.example.test/#/order-confirmation","TWILIO_ACCOUNT_SID=","TWILIO_AUTH_TOKEN=","SMTP_HOST=","SMTP_FROM=","SMTP_PASSWORD=environment-test-credential","ONLINE_ORDERING_ENABLED=false"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class ConfigurationIntegrationTest {
 static final String PATH="/api/staff/restaurant/configuration",MASTER=CredentialCipherTest.key();
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv("DB_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));p.add("NAKORN_CREDENTIAL_MASTER_KEY",()->MASTER);
 }
 @Autowired MockMvc mvc;@Autowired ConfigurationHandler handler;@Autowired RuntimeConfiguration runtime;
 @Autowired EntityManager em;@Autowired JdbcTemplate jdbc;@Autowired Environment environment;@Autowired java.time.Clock clock;
 @MockitoBean IntegrationDiagnostics diagnostics;
 @Autowired au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider paypal;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Test
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void adminReadWorksWithoutWritableTestTransaction()throws Exception{
  mvc.perform(get(PATH).with(user("owner").roles("ADMIN")))
    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
    .andExpect(jsonPath("$.SETTINGS.version").isNumber())
    .andExpect(jsonPath("$.PAYID.fields.identifier").value("fallback@example.test"));
 }
 @Test
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void snapshotWorksInGenuinelyReadOnlyPostgresTransactionWithoutRowLock(){
  var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  transaction.setReadOnly(true);
  transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
  transaction.executeWithoutResult(status->{
   assertEquals("on",jdbc.queryForObject("SHOW transaction_read_only",String.class));
   assertEquals("repeatable read",jdbc.queryForObject("SHOW transaction_isolation",String.class));
   assertEquals("fallback@example.test",runtime.snapshot().text("identifier"));
   assertEquals(jakarta.persistence.LockModeType.NONE,em.getLockMode(em.find(RestaurantSettingsJpaEntity.class,(short)1)));
  });
 }
 @Test
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void adminReadDoesNotWaitForConfigurationWriterLock(){
  var reader=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{
   new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
    var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE,em.getLockMode(settings));
    var response=reader.submit(()->mvc.perform(get(PATH).with(user("owner").roles("ADMIN")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.SETTINGS.version").isNumber()));
    // The writer still holds its real PostgreSQL row lock when the independent HTTP read finishes.
    assertDoesNotThrow(()->response.get(10,java.util.concurrent.TimeUnit.SECONDS));
   });
  }finally{reader.shutdownNow();}
 }
 long version(String c){return ((Number)((Map<?,?>)handler.read().get(c)).get("version")).longValue();}
 void save(String category,Map<String,String> fields,Map<String,String> secrets){handler.save(category,new ConfigurationHandler.Update(version(category),fields,secrets,Set.of(),false),"owner");}
 String body(String c,String fields,String secrets){return "{\"version\":"+version(c)+",\"fields\":"+fields+",\"secrets\":"+secrets+"}";}
 String metadata()throws Exception{return mvc.perform(get(PATH).with(user("owner").roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();}
 @Test void onlyAdminHasAccessAndWritesRequireCsrf()throws Exception{
  mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  for(String role:List.of("FOH","BOH")){
   mvc.perform(get(PATH).with(user("staff").roles(role))).andExpect(status().isForbidden());
   mvc.perform(put(PATH+"/PAYID").with(user("staff").roles(role)).with(csrf()).contentType("application/json").content(body("PAYID","{}","{}"))).andExpect(status().isForbidden());
   mvc.perform(post(PATH+"/PAYID/test").with(user("staff").roles(role)).with(csrf()).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
   mvc.perform(get(PATH+"/audit").with(user("staff").roles(role))).andExpect(status().isForbidden());
  }
  mvc.perform(put(PATH+"/PAYID").with(user("owner").roles("ADMIN")).contentType("application/json").content(body("PAYID","{}","{}"))).andExpect(status().isForbidden());
  mvc.perform(put(PATH+"/PAYID").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("PAYID","{\"identifier\":\"dashboard@example.test\",\"accountName\":\"Restaurant\"}","{}"))).andExpect(status().isOk()).andExpect(jsonPath("$.PAYID.fields.identifier").value("dashboard@example.test"));
 }
 @Test void databaseOverrideAndEnvironmentFallbackAreExplicit()throws Exception{
  assertEquals("fallback@example.test",runtime.snapshot().text("identifier"));
  assertTrue(metadata().contains("ENVIRONMENT_DEFAULT"));save("PAYID",Map.of("identifier","dashboard@example.test"),Map.of());
  assertEquals("dashboard@example.test",runtime.snapshot().text("identifier"));assertTrue(metadata().contains("DASHBOARD"));
 }
 @Test void secretsPersistEncryptedResolveAndNeverAppearInApiOrAudit()throws Exception{
  String secret="test-only-credential";save("PAYPAL",Map.of("clientId","merchant","environment","sandbox"),Map.of("clientSecret",secret));
  String stored=jdbc.queryForObject("select secrets->>'clientSecret' from integration_configuration where category='PAYPAL'",String.class);
  assertTrue(stored.startsWith("v1:")&&!stored.contains(secret));assertTrue(runtime.snapshot().text("clientSecret").equals(secret));
  String response=metadata();assertTrue(!response.contains(secret)&&!response.contains(stored));assertTrue(response.contains("secretsConfigured"));
  String audit=mvc.perform(get(PATH+"/audit").with(user("owner").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(audit.contains("owner")&&audit.contains("PAYPAL_CONFIGURATION_UPDATED")&&!audit.contains(secret)&&!audit.contains(stored));
 }
 @Test void blankUpdatesPreserveSecretReplacementChangesItAndExplicitClearSuppressesFallback(){
  save("PAYPAL",Map.of("clientId","merchant"),Map.of("clientSecret","first-test-secret"));
  String stored=em.find(IntegrationConfigurationJpaEntity.class,"PAYPAL").getSecrets().get("clientSecret");
  save("PAYPAL",Map.of("clientId","other-merchant"),Map.of("clientSecret",""));assertTrue(runtime.snapshot().text("clientSecret").equals("first-test-secret"));assertEquals(stored,em.find(IntegrationConfigurationJpaEntity.class,"PAYPAL").getSecrets().get("clientSecret"));
  save("PAYPAL",Map.of(),Map.of("clientSecret","second-test-secret"));assertTrue(runtime.snapshot().text("clientSecret").equals("second-test-secret"));
  handler.save("PAYPAL",new ConfigurationHandler.Update(version("PAYPAL"),Map.of(),Map.of(),Set.of("clientSecret"),false),"owner");assertEquals("",runtime.snapshot().text("clientSecret"));
 }
 @Test void staleUpdateReturns409AndDoesNotOverwrite()throws Exception{
  long old=version("PAYID");save("PAYID",Map.of("identifier","new@example.test"),Map.of());
  mvc.perform(put(PATH+"/PAYID").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":"+old+",\"fields\":{\"identifier\":\"stale@example.test\"}}")).andExpect(status().isConflict());
  assertEquals("new@example.test",runtime.snapshot().text("identifier"));
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"paypalEnabled","orderPhoneRequired","reservationPhoneRequired","orderSms","reservationSms","orderEmail","reservationEmail"})
 void missingIntegrationDependenciesAreRejected(String toggle)throws Exception{
  mvc.perform(put(PATH+"/SETTINGS").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("SETTINGS","{\""+toggle+"\":\"true\"}","{}"))).andExpect(status().isBadRequest());
 }
 @Test void payidCannotEnableWhenInstructionsAreIncomplete(){
  save("PAYID",Map.of("identifier","","accountName",""),Map.of());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->save("SETTINGS",Map.of("payidEnabled","true"),Map.of()));
 }
 @Test void configuredProvidersAllowBusinessSettingsAndSettingsCanBeDisabled(){
  save("TWILIO",Map.of("accountSid","AC"+"a".repeat(32),"verifyServiceSid","VA"+"b".repeat(32),"smsFrom","+61412345678","verifySmsEnabled","true"),Map.of("authToken","test-token"));
  save("SMTP",Map.of("host","smtp.example.test","port","587","from","restaurant@example.test"),Map.of());
  save("SETTINGS",Map.of("orderingEnabled","true","orderPhoneRequired","true","reservationPhoneRequired","true","orderSms","true","reservationSms","true","orderEmail","true","reservationEmail","true","payidEnabled","true"),Map.of());
  assertTrue(runtime.snapshot().flag("orderingEnabled"));save("SETTINGS",Map.of("orderingEnabled","false","reservationsEnabled","false"),Map.of());assertFalse(runtime.snapshot().flag("orderingEnabled"));
 }
 @Test void lastPaymentMethodCannotBeDisabledWhileOrderingEnabled()throws Exception{
  mvc.perform(put(PATH+"/SETTINGS").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("SETTINGS","{\"orderingEnabled\":\"true\",\"payAtRestaurantEnabled\":\"false\",\"paypalEnabled\":\"false\",\"payidEnabled\":\"false\"}","{}"))).andExpect(status().isBadRequest());
 }
 @Test void livePaypalRequiresValidationAndConfigurationChangeDisablesCheckout(){
  save("PAYPAL",Map.of("clientId","merchant","environment","live"),Map.of("clientSecret","test-secret"));
  assertFalse(runtime.snapshot().flag("paypalEnabled"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->save("SETTINGS",Map.of("paypalEnabled","true"),Map.of()));
  var result=handler.test("PAYPAL",version("PAYPAL"),"owner",null);assertEquals("VALID",result.get("status"));verify(diagnostics).test("PAYPAL");
  save("SETTINGS",Map.of("paypalEnabled","true"),Map.of());assertTrue(runtime.snapshot().flag("paypalEnabled"));assertTrue(paypal.enabled());
  save("PAYPAL",Map.of(),Map.of("clientSecret","replacement-test-secret"));assertFalse(runtime.snapshot().flag("paypalEnabled"));assertFalse(runtime.snapshot().flag("paypalValidated"));
 }
 @Test void testFailureIsSafeAndDoesNotExposeProviderExceptions(){
  doThrow(new IllegalStateException("sensitive provider test detail")).when(diagnostics).test("PAYID");
  var result=handler.test("PAYID",version("PAYID"),"owner",null);assertEquals("INVALID",result.get("status"));assertFalse(result.toString().contains("sensitive provider test detail"));
 }
 @Test void testEmailRequiresExplicitAdminActionAndSavingNeverSendsEmail()throws Exception{
  save("SMTP",Map.of("host","smtp.example.test","from","restaurant@example.test"),Map.of());verifyNoInteractions(diagnostics);
  mvc.perform(post(PATH+"/SMTP/test-email").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":"+version("SMTP")+",\"recipient\":\"tester@example.test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));verify(diagnostics).testEmail("tester@example.test");
 }
 @Test void storedCredentialsRequireCorrectMasterKeyAtStartupWithoutFallback(){
  save("PAYPAL",Map.of(),Map.of("clientSecret","test-secret"));
  for(String key:List.of("", "invalid",CredentialCipherTest.key())){
   var resolver=new DatabaseRuntimeConfiguration(em,environment,new AesCredentialCipher(key));
   assertThrows(IllegalStateException.class,()->resolver.run(null));assertThrows(IllegalStateException.class,resolver::snapshot);
  }
  assertDoesNotThrow(()->new DatabaseRuntimeConfiguration(em,environment,new AesCredentialCipher(MASTER)).run(null));
 }
 @Test void pendingPayidRequiresAcknowledgement()throws Exception{
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,payment_method,created_at,updated_at) VALUES (?,?,?,'Test Customer','+61412345678','',1990,'PAYID',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"a".repeat(64),"b".repeat(64));
  mvc.perform(put(PATH+"/PAYID").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("PAYID","{\"identifier\":\"new-bank@example.test\"}","{}"))).andExpect(status().isBadRequest());
 }
 @Test void acknowledgedPendingPayidUpdateWarnsInMetadataAndKeepsOrderUnpaid()throws Exception{
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,payment_method,created_at,updated_at) VALUES (?,?,?,'Test Customer','+61412345678','',1990,'PAYID',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"a".repeat(64),"b".repeat(64));
  handler.save("PAYID",new ConfigurationHandler.Update(version("PAYID"),Map.of("identifier","new-bank@example.test"),Map.of(),Set.of(),true),"owner");
  assertTrue(((Number)((Map<?,?>)handler.read().get("PAYID")).get("pendingPayments")).intValue()>0);
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void pendingPaypalBlocksCredentialIdentityChange()throws Exception{
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,payment_method,created_at,updated_at) VALUES (?,?,?,'Test Customer','+61412345678','',1990,'PAYPAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"a".repeat(64),"b".repeat(64));
  mvc.perform(put(PATH+"/PAYPAL").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("PAYPAL","{\"clientId\":\"new-merchant\"}","{\"clientSecret\":\"test-secret\"}"))).andExpect(status().isBadRequest());
 }
 @Test void existingRestaurantSettingsAlsoHaveDurableActorAudit()throws Exception{
  mvc.perform(put("/api/staff/restaurant/settings").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":"+version("SETTINGS")+",\"timezone\":\"Australia/Sydney\"}")).andExpect(status().isOk());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM configuration_audit WHERE actor='owner' AND action='RESTAURANT_SETTINGS_UPDATED'",Integer.class));
 }
 @Test void disabledMandatoryVerificationDoesNotClaimPhoneVerifiedOrQueueSms()throws Exception{
  save("SMTP",Map.of("host","smtp.example.test","from","restaurant@example.test"),Map.of());
  save("SETTINGS",Map.of("reservationPhoneRequired","false","reservationSms","false","reservationEmail","true"),Map.of());
  mvc.perform(get("/api/reservations/options")).andExpect(status().isOk()).andExpect(jsonPath("$.phoneRequired").value(false));
  jdbc.update("DELETE FROM restaurant_closed_date");jdbc.update("DELETE FROM restaurant_opening_hours");
  for(int day=1;day<=7;day++){
   jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,?,'00:00','12:00')",UUID.randomUUID(),day);
   jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,?,'12:00','00:00')",UUID.randomUUID(),day);
  }
  UUID id=UUID.randomUUID();String timezone=jdbc.queryForObject("SELECT timezone FROM restaurant_settings WHERE id=1",String.class);
  var requested=java.time.LocalDateTime.ofInstant(clock.instant(),java.time.ZoneId.of(timezone)).plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0);
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content("{\"requestId\":\""+id+"\",\"customerName\":\"Guest\",\"phone\":\"0412345678\",\"email\":\"guest@example.test\",\"partySize\":2,\"requestedAt\":\""+requested+"\",\"notes\":\"\"}")).andExpect(status().isCreated());
  em.flush();
  assertFalse(jdbc.queryForObject("SELECT phone_verified FROM reservation WHERE id=?",Boolean.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND channel='EMAIL'",Integer.class,id));
 }
 @Test void missingRequiredVerificationProviderMakesPublicOptionsUnavailable()throws Exception{
  mvc.perform(get("/api/reservations/options")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
 }


 @Test void secretEnvironmentFallbackIsOverriddenAndExplicitClearNeverRestoresIt()throws Exception{
  assertTrue(runtime.snapshot().text("password").equals("environment-test-credential"));
  save("SMTP",Map.of("host","smtp.example.test","from","restaurant@example.test"),Map.of("password","dashboard-test-credential"));
  assertTrue(runtime.snapshot().text("password").equals("dashboard-test-credential"));
  handler.save("SMTP",new ConfigurationHandler.Update(version("SMTP"),Map.of(),Map.of(),Set.of("password"),false),"owner");
  assertEquals("",runtime.snapshot().text("password"));assertFalse(metadata().contains("environment-test-credential"));
 }
 @Test void businessSettingsNeverAcceptCredentialFields()throws Exception{
  mvc.perform(put(PATH+"/SETTINGS").with(user("owner").roles("ADMIN")).with(csrf()).contentType("application/json").content(body("SETTINGS","{}","{\"clientSecret\":\"test-credential\"}"))).andExpect(status().isBadRequest());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM configuration_audit WHERE category='SETTINGS'",Integer.class));
 }

}
