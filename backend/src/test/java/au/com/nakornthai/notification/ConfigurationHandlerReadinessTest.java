package au.com.nakornthai.notification;

import au.com.nakornthai.restaurant.configuration.*;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationHandlerReadinessTest {
 @Test void defaultTwilioDashboardRetainsProblemStateForMissingService() {
  var view=view(Map.of());assertEquals("PROBLEM",view.get("state"));assertEquals(false,view.get("enabled"));
 }
 @Test void inactiveTwilioCapabilityFlagsRemainTwilioSpecific() {
  var view=view(Map.of("smsProvider","vonage","verificationProvider","vonage"));
  assertEquals("CONFIGURED",view.get("state"));assertEquals(false,view.get("enabled"));assertEquals(false,view.get("smsVerificationConfigured"));assertEquals(true,view.get("smsSendingConfigured"));
 }
 @Test void vonageVerificationDoesNotBlockSelectedTwilioSms() {
  var view=view(Map.of("smsProvider","twilio","verificationProvider","vonage"));
  assertEquals("ENABLED",view.get("state"));assertEquals(true,view.get("enabled"));assertEquals(false,view.get("smsVerificationConfigured"));
 }
 @Test void selectedTwilioVerificationStillRequiresItsService() {
  assertEquals("PROBLEM",view(Map.of("smsProvider","vonage","verificationProvider","twilio")).get("state"));
 }
 @SuppressWarnings("unchecked") private Map<String,Object> view(Map<String,String> selection) {
  var values=new HashMap<>(Map.of("accountSid","AC"+"a".repeat(32),"authToken","test-token","verifySmsEnabled","true","smsFrom","+61412345678","orderSms","true"));values.putAll(selection);
  RuntimeConfiguration runtime=()->new RuntimeConfiguration.Snapshot(values);
  EntityManager em=mock(EntityManager.class,RETURNS_DEEP_STUBS);
  var settings=new RestaurantSettingsJpaEntity();settings.setOperationalConfiguration(Map.of());
  when(em.find(RestaurantSettingsJpaEntity.class,(short)1)).thenReturn(settings);
  for(String category:List.of("PAYPAL","PAYID","TWILIO","SMTP")) {var row=new IntegrationConfigurationJpaEntity();row.setCategory(category);when(em.find(IntegrationConfigurationJpaEntity.class,category)).thenReturn(row);}
  when(em.createNativeQuery(anyString(),eq(Long.class)).setParameter(eq("method"),anyString()).getSingleResult()).thenReturn(0L);
  var handler=new ConfigurationHandler(em,runtime,mock(CredentialCipher.class),mock(IntegrationDiagnostics.class),Clock.systemUTC(),mock(PlatformTransactionManager.class),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  return (Map<String,Object>)handler.read().get("TWILIO");
 }
}
