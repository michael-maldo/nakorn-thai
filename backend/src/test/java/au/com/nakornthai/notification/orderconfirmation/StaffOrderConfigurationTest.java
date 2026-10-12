package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.restaurant.configuration.*;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffOrderConfigurationTest {
 @Test void defaultsAreSafeAndDashboardOverridesEnvironmentOnEveryRead(){
  var em=mock(EntityManager.class);var rows=mock(TypedQuery.class);when(em.createQuery("from IntegrationConfigurationJpaEntity",IntegrationConfigurationJpaEntity.class)).thenReturn(rows);when(rows.getResultList()).thenReturn(List.of());
  var settings=new RestaurantSettingsJpaEntity();when(em.find(RestaurantSettingsJpaEntity.class,(short)1)).thenReturn(settings);
  var environment=new MockEnvironment();var runtime=new DatabaseRuntimeConfiguration(em,environment,mock(CredentialCipher.class));
  assertFalse(runtime.snapshot().flag("staffOrderEmail"));assertEquals("hello@nakorn.com.au",runtime.snapshot().text("staffOrderEmailAddress"));
  environment.setProperty("STAFF_ORDER_EMAIL_ADDRESS","environment@example.test");environment.setProperty("STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED","true");assertTrue(runtime.snapshot().flag("staffOrderEmail"));
  settings.setOperationalConfiguration(Map.of("staffOrderEmail","false","staffOrderEmailAddress","admin@example.test"));assertFalse(runtime.snapshot().flag("staffOrderEmail"));assertEquals("admin@example.test",runtime.snapshot().text("staffOrderEmailAddress"));
  settings.setOperationalConfiguration(Map.of("staffOrderEmail","true","staffOrderEmailAddress","changed@example.test"));assertTrue(runtime.snapshot().flag("staffOrderEmail"));assertEquals("changed@example.test",runtime.snapshot().text("staffOrderEmailAddress"));
 }
 @Test void enabledStaffEmailRequiresUsableRecipientAndSmtp(){
  for(var values:List.of(Map.of("staffOrderEmail","true","staffOrderEmailAddress",""),Map.of("staffOrderEmail","true","staffOrderEmailAddress","invalid"),Map.of("staffOrderEmail","true","staffOrderEmailAddress","staff@example.test"))){
   var em=mock(EntityManager.class);var settings=mock(RestaurantSettingsJpaEntity.class);when(settings.getVersion()).thenReturn(0L);when(settings.getOperationalConfiguration()).thenReturn(Map.of());when(em.find(RestaurantSettingsJpaEntity.class,(short)1,LockModeType.PESSIMISTIC_WRITE)).thenReturn(settings);
   RuntimeConfiguration runtime=()->new RuntimeConfiguration.Snapshot(values);
   var handler=new ConfigurationHandler(em,runtime,mock(CredentialCipher.class),mock(IntegrationDiagnostics.class),Clock.systemUTC(),mock(PlatformTransactionManager.class),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
   var failure=assertThrows(ResponseStatusException.class,()->handler.save("SETTINGS",new ConfigurationHandler.Update(0L,Map.of("staffOrderEmail","true"),Map.of(),Set.of(),false),"owner"));assertTrue(failure.getReason().contains("usable staff"));verify(em,never()).flush();
  }
 }
}
