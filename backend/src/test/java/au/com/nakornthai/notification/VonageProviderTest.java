package au.com.nakornthai.notification;

import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.notification.infrastructure.sms.VonageSmsSender;
import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VonageProviderTest {
 RuntimeConfiguration configuration=()->new RuntimeConfiguration.Snapshot(Map.of("vonageApiKey","test-key","vonageApiSecret","test-secret","vonageSmsFrom","TestSender","vonageVerifyBrand","TestBrand","vonageVerifySmsEnabled","true"));
 RestClient.Builder builder=RestClient.builder().baseUrl("https://rest.nexmo.com");
 MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
 @Test void smsSuccess() {
  server.expect(requestTo("https://rest.nexmo.com/sms/json")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ=")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(content().string("from=TestSender&to=61412345678&text=Hello&type=unicode")).andRespond(withSuccess("{\"messages\":[{\"status\":\"0\"}]}",MediaType.APPLICATION_JSON));
  new VonageSmsSender(configuration,builder.build()).send("+61412345678","Hello");server.verify();
 }
 @Test void smsUnicodeAndFormEscaping() {
  var form=new org.springframework.util.LinkedMultiValueMap<String,String>();form.add("from","TestSender");form.add("to","61412345678");form.add("text","สวัสดี & +");form.add("type","unicode");
  server.expect(requestTo("https://rest.nexmo.com/sms/json")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(content().formData(form)).andRespond(withSuccess("{\"messages\":[{\"status\":\"0\"}]}",MediaType.APPLICATION_JSON));
  new VonageSmsSender(configuration,builder.build()).send("+61412345678","สวัสดี & +");server.verify();
 }
 @Test void smsApiFailure() {smsFailure(withSuccess("{\"messages\":[{\"status\":\"9\"}]}",MediaType.APPLICATION_JSON));}
 @Test void smsHttpFailure() {smsFailure(withStatus(HttpStatus.UNAUTHORIZED));}
 @Test void smsTransportFailure() {smsFailure(withException(new java.net.http.HttpTimeoutException("simulated timeout")));}
 @Test void smsPartiallyRejected() {smsFailure(withSuccess("{\"messages\":[{\"status\":\"0\"},{\"status\":\"9\"}]}",MediaType.APPLICATION_JSON));}
 @Test void smsMalformedResponse() {smsFailure(withSuccess("{}",MediaType.APPLICATION_JSON));}
 void smsFailure(org.springframework.test.web.client.ResponseCreator response) {
  server.expect(requestTo("https://rest.nexmo.com/sms/json")).andRespond(response);
  var error=assertThrows(IllegalStateException.class,()->new VonageSmsSender(configuration,builder.build()).send("+61412345678","Hello"));
  assertEquals("SMS delivery unavailable",error.getMessage());assertNull(error.getCause());server.verify();
 }
 void verificationBoundary() {builder=RestClient.builder().baseUrl("https://api.nexmo.com");server=MockRestServiceServer.bindTo(builder).build();}
 @Test void verifyStartSuccess() {
  verificationBoundary();
  server.expect(requestTo("https://api.nexmo.com/verify/json")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ=")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(content().string("number=61412345678&brand=TestBrand&workflow_id=6&code_length=6&pin_expiry=600&next_event_wait=600")).andRespond(withSuccess("{\"status\":\"0\",\"request_id\":\"opaque-reference\"}",MediaType.APPLICATION_JSON));
  assertEquals("opaque-reference",new VonageVerifyClient(configuration,builder.build()).start("+61412345678","sms"));server.verify();
 }
 @Test void verifyStartFailure() {
  verificationBoundary();
  server.expect(requestTo("https://api.nexmo.com/verify/json")).andRespond(withSuccess("{\"status\":\"9\"}",MediaType.APPLICATION_JSON));
  assertEquals(503,assertThrows(ResponseStatusException.class,()->new VonageVerifyClient(configuration,builder.build()).start("+61412345678","sms")).getStatusCode().value());server.verify();
 }
 @Test void verifyStartHttpFailure() {verifyStartFailure(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));}
 @Test void verifyStartMissingReference() {verifyStartFailure(withSuccess("{\"status\":\"0\"}",MediaType.APPLICATION_JSON));}
 void verifyStartFailure(org.springframework.test.web.client.ResponseCreator response) {
  verificationBoundary();server.expect(requestTo("https://api.nexmo.com/verify/json")).andRespond(response);
  var error=assertThrows(ResponseStatusException.class,()->new VonageVerifyClient(configuration,builder.build()).start("+61412345678","sms"));
  assertEquals(503,error.getStatusCode().value());assertNull(error.getCause());server.verify();
 }
 @Test void verifyCheckSuccess() {assertTrue(check("0"));}
 @Test void verifyIncorrectCode() {assertFalse(check("16"));}
 @Test void verifyAttemptLimit() {assertFalse(check("17"));}
 @Test void verifyMissingRequest() {assertFalse(check("101"));}
 @Test void verifyHttpFailure() {verifyCheckFailure(withStatus(HttpStatus.UNAUTHORIZED));}
 @Test void verifyThrottledHttp() {verifyCheckFailure(withStatus(HttpStatus.TOO_MANY_REQUESTS));}
 @Test void verifyTransportFailure() {verifyCheckFailure(withException(new java.net.http.HttpTimeoutException("simulated timeout")));}
 @Test void verifyMalformedResponse() {verifyCheckFailure(withSuccess("{}",MediaType.APPLICATION_JSON));}
 void verifyCheckFailure(org.springframework.test.web.client.ResponseCreator response) {
  verificationBoundary();server.expect(requestTo("https://api.nexmo.com/verify/check/json")).andRespond(response);
  var error=assertThrows(ResponseStatusException.class,()->new VonageVerifyClient(configuration,builder.build()).check("opaque-reference","123456"));
  assertEquals(503,error.getStatusCode().value());assertNull(error.getCause());server.verify();
 }
 @Test void verifyProviderFailure() {assertEquals(503,assertThrows(ResponseStatusException.class,()->check("5")).getStatusCode().value());}
 @Test void verifyUnexpectedStatus() {assertEquals(503,assertThrows(ResponseStatusException.class,()->check("999")).getStatusCode().value());}
 @Test void verifyInvalidCredentialsStatus() {assertEquals(503,assertThrows(ResponseStatusException.class,()->check("4")).getStatusCode().value());}
 @Test void verifyInvalidParametersStatus() {assertEquals(503,assertThrows(ResponseStatusException.class,()->check("3")).getStatusCode().value());}
 @Test void verifyUnroutableStatusIsNotAnExpiredChallenge() {assertEquals(503,assertThrows(ResponseStatusException.class,()->check("6")).getStatusCode().value());}
 boolean check(String status) {
  verificationBoundary();
  server.expect(requestTo("https://api.nexmo.com/verify/check/json")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ=")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(content().string("request_id=opaque-reference&code=123456")).andRespond(withSuccess("{\"status\":\""+status+"\"}",MediaType.APPLICATION_JSON));
  try{return new VonageVerifyClient(configuration,builder.build()).check("opaque-reference","123456");}finally{server.verify();}
 }
 @Test void sensitiveProviderFailureIsNotLoggedOrExposed() {
  var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(VonageVerifyClient.class);
  var events=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();events.start();logger.addAppender(events);
  try {
   verifyCheckFailure(withStatus(HttpStatus.UNAUTHORIZED).body("test-secret 123456 Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ="));
   assertFalse(events.list.isEmpty());
   for(var event:events.list) {
    String message=event.getFormattedMessage();assertFalse(message.contains("test-secret"));assertFalse(message.contains("123456"));assertFalse(message.contains("Basic "));assertNull(event.getThrowableProxy());
   }
  }finally {logger.detachAppender(events);events.stop();}
 }
 @Test void unsupportedEmailAndDisabledSmsMakeNoCalls() {
  var provider=new VonageVerifyClient(configuration,builder.build());assertTrue(provider.enabled("sms"));assertFalse(provider.enabled("email"));assertThrows(ResponseStatusException.class,()->provider.start("test@example.test","email"));
  var disabled=new VonageVerifyClient(()->new RuntimeConfiguration.Snapshot(Map.of()),builder.build());assertFalse(disabled.enabled("sms"));assertThrows(ResponseStatusException.class,()->disabled.start("+61412345678","sms"));server.verify();
 }
}
