package au.com.nakornthai.notification;

import au.com.nakornthai.notification.infrastructure.TwilioConnectionDiagnostics;
import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TwilioConnectionDiagnosticsTest {
 String account="AC"+"a".repeat(32),service="VA"+"b".repeat(32);
 RestClient.Builder accounts=RestClient.builder().baseUrl("https://api.twilio.com/2010-04-01");
 RestClient.Builder verification=RestClient.builder().baseUrl("https://verify.twilio.com/v2");
 MockRestServiceServer accountServer=MockRestServiceServer.bindTo(accounts).build();
 MockRestServiceServer verifyServer=MockRestServiceServer.bindTo(verification).build();
 RuntimeConfiguration configuration(String service) {return ()->new RuntimeConfiguration.Snapshot(Map.of("accountSid",account,"authToken","test-token","verifyServiceSid",service,"verificationProvider","vonage"));}
 @Test void validatesAccountAndOptionalServiceWhenVonageIsSelected() {
  String authentication="Basic "+java.util.Base64.getEncoder().encodeToString((account+":test-token").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
  accountServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/"+account+".json")).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization",authentication)).andRespond(withSuccess());
  verifyServer.expect(requestTo("https://verify.twilio.com/v2/Services/"+service)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization",authentication)).andRespond(withSuccess());
  new TwilioConnectionDiagnostics(configuration(service),accounts.build(),verification.build()).testConnection();accountServer.verify();verifyServer.verify();
 }
 @Test void missingServiceOnlyTestsAccount() {
  accountServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/"+account+".json")).andRespond(withSuccess());
  new TwilioConnectionDiagnostics(configuration(""),accounts.build(),verification.build()).testConnection();accountServer.verify();verifyServer.verify();
 }
 @Test void failureDoesNotExposeResponse() {
  accountServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/"+account+".json")).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive provider response"));
  var error=assertThrows(IllegalStateException.class,()->new TwilioConnectionDiagnostics(configuration(service),accounts.build(),verification.build()).testConnection());
  assertEquals("Twilio validation unavailable",error.getMessage());assertNull(error.getCause());accountServer.verify();verifyServer.verify();
 }
}
