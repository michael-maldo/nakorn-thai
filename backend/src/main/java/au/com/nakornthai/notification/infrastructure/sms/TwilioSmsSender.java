package au.com.nakornthai.notification.infrastructure.sms;
import au.com.nakornthai.notification.domain.SmsSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import java.net.http.HttpClient;
import java.time.Duration;
@Component
public class TwilioSmsSender implements SmsSender {
 private final String account,secret,from;
 private final RestClient api;
 public TwilioSmsSender(@Value("${TWILIO_ACCOUNT_SID:}") String account,@Value("${TWILIO_AUTH_TOKEN:}") String secret,@Value("${TWILIO_SMS_FROM:}") String from) {
  this.account=account;this.secret=secret;this.from=from;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(10));
  api=RestClient.builder().requestFactory(factory).baseUrl("https://api.twilio.com/2010-04-01").build();
 }
 public void send(String recipient,String message) {
  if(account.isBlank() || secret.isBlank() || from.isBlank())throw new IllegalStateException("SMS delivery unavailable");
  var body=new LinkedMultiValueMap<String,String>();body.add("To",recipient);body.add("From",from);body.add("Body",message);
  try {api.post().uri("/Accounts/{account}/Messages.json",account).headers(h->h.setBasicAuth(account,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().toBodilessEntity();}
  catch(Exception e){throw new IllegalStateException("SMS delivery unavailable");}
 }
}
