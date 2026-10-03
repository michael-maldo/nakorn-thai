package au.com.nakornthai.notification;
import au.com.nakornthai.notification.contactverification.*;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContactVerificationTest {
 EntityManager em=mock(EntityManager.class,RETURNS_DEEP_STUBS);
 ContactVerificationProvider provider=mock(ContactVerificationProvider.class);
 Clock clock=Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"),ZoneOffset.UTC);
 ContactVerificationHandler handler=new ContactVerificationHandler(em,provider,clock,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
 ContactVerificationJpaEntity entry=new ContactVerificationJpaEntity();
 UUID id=UUID.randomUUID();
 @BeforeEach void setup(){entry.setId(id);entry.setChannel("SMS");entry.setDestinationHash(CreateOrderHandler.hash("SMS:+61412345678"));entry.setCreatedAt(clock.instant());entry.setExpiresAt(clock.instant().plusSeconds(600));entry.setProviderReference("reference");when(em.find(ContactVerificationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(entry);}
 @Test void normalization(){assertEquals("+61412345678",ContactDestination.normalize("SMS","0412 345 678"));assertEquals("Guest@example.com",ContactDestination.normalize("EMAIL"," Guest@EXAMPLE.COM "));assertThrows(ResponseStatusException.class,()->ContactDestination.normalize("SMS","abc"));assertThrows(ResponseStatusException.class,()->ContactDestination.normalize("SMS","0412"));}
 @Test void unverifiedRejected(){assertThrows(ResponseStatusException.class,()->handler.consume(id,"SMS","+61412345678",UUID.randomUUID()));}
 @Test void invalidIdRejected(){assertThrows(ResponseStatusException.class,()->handler.consume(UUID.randomUUID(),"SMS","+61412345678",UUID.randomUUID()));}
 @Test void expiredRejected(){entry.setVerifiedAt(clock.instant());entry.setExpiresAt(clock.instant());assertThrows(ResponseStatusException.class,()->handler.consume(id,"SMS","+61412345678",UUID.randomUUID()));}
 @Test void mismatchRejected(){entry.setVerifiedAt(clock.instant());assertThrows(ResponseStatusException.class,()->handler.consume(id,"SMS","+61499999999",UUID.randomUUID()));assertThrows(ResponseStatusException.class,()->handler.consume(id,"EMAIL","+61412345678",UUID.randomUUID()));}
 @Test void successfulChallengeIsSingleUse(){when(provider.check("reference","123456")).thenReturn(true);assertEquals(true,handler.verify(id,"123456").get("verified"));assertTrue(handler.consume(id,"SMS","+61412345678",UUID.randomUUID()));assertThrows(ResponseStatusException.class,()->handler.consume(id,"SMS","+61412345678",UUID.randomUUID()));}
 @Test void fiveAttemptsOnly(){for(int i=0;i<6;i++)assertThrows(ResponseStatusException.class,()->handler.verify(id,"123456"));assertEquals(5,entry.getAttempts());verify(provider,times(5)).check("reference","123456");}
 @Test void unavailableChannel(){assertEquals(503,assertThrows(ResponseStatusException.class,()->handler.start("SMS","0412345678")).getStatusCode().value());}
 @Test void rateLimit(){when(provider.enabled("sms")).thenReturn(true);when(em.createNativeQuery(anyString(),eq(Long.class)).setParameter(eq("hash"),any()).setParameter(eq("since"),any()).getSingleResult()).thenReturn(5L);assertEquals(429,assertThrows(ResponseStatusException.class,()->handler.start("SMS","0412345678")).getStatusCode().value());verify(provider,never()).start(any(),any());}
}
