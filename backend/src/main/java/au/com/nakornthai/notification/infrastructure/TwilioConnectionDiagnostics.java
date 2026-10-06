package au.com.nakornthai.notification.infrastructure;

import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

/** Dashboard credential validation is independent of the selected verification provider. */
@Component
public class TwilioConnectionDiagnostics {
 private final RuntimeConfiguration configuration;
 private final RestClient accounts;
 private final RestClient verification;
 @Autowired
 public TwilioConnectionDiagnostics(RuntimeConfiguration configuration) {
  this(configuration,client("https://api.twilio.com/2010-04-01"),client("https://verify.twilio.com/v2"));
 }
 public TwilioConnectionDiagnostics(RuntimeConfiguration configuration,RestClient accounts,RestClient verification) {
  this.configuration=configuration;this.accounts=accounts;this.verification=verification;
 }
 private static RestClient client(String baseUrl) {
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  factory.setReadTimeout(Duration.ofSeconds(10));
  return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
 }
 public void testConnection() {
  var c=configuration.snapshot();
  try {
   accounts.get().uri("/Accounts/{account}.json",c.text("accountSid")).headers(h->h.setBasicAuth(c.text("accountSid"),c.text("authToken"))).retrieve().toBodilessEntity();
   if(!c.text("verifyServiceSid").isBlank())verification.get().uri("/Services/{service}",c.text("verifyServiceSid")).headers(h->h.setBasicAuth(c.text("accountSid"),c.text("authToken"))).retrieve().toBodilessEntity();
  }catch(Exception failure) {throw new IllegalStateException("Twilio validation unavailable");}
 }
}
