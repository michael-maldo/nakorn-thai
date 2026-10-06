package au.com.nakornthai.notification.infrastructure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="notification.verification-provider", havingValue="twilio", matchIfMissing=true)
public class TwilioVerifyClient implements au.com.nakornthai.notification.domain.ContactVerificationProvider {
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 @org.springframework.beans.factory.annotation.Autowired
 public TwilioVerifyClient(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration) {this("","","",false,false);this.configuration=configuration;}
 private TwilioVerifyClient configured() {var c=configuration.snapshot();return new TwilioVerifyClient(c.text("accountSid"),c.text("authToken"),c.text("verifyServiceSid"),c.flag("verifySmsEnabled"),c.flag("verifyEmailEnabled"));}
 private final RestClient api;private final String service,account,secret;private final boolean sms,email;
 public TwilioVerifyClient(@Value("${TWILIO_ACCOUNT_SID:}") String account,@Value("${TWILIO_AUTH_TOKEN:}") String secret,@Value("${TWILIO_VERIFY_SERVICE_SID:}") String service,@Value("${VERIFY_SMS_ENABLED:false}") boolean sms,@Value("${VERIFY_EMAIL_ENABLED:false}") boolean email) {
  this.account=account;this.secret=secret;this.service=service;this.sms=sms;this.email=email;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(10));
  api=RestClient.builder().requestFactory(factory).baseUrl("https://verify.twilio.com/v2").build();
 }
 public boolean enabled(String channel){if(configuration!=null)return configured().enabled(channel);return account.matches("AC[0-9a-fA-F]{32}") && !secret.isBlank() && service.matches("VA[0-9a-fA-F]{32}") && (channel.equals("sms")?sms:channel.equals("email")&&email);}
 public String start(String to,String channel){
  if(configuration!=null)return configured().start(to,channel);
  if(!enabled(channel))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Verification channel is unavailable");
  var body=new LinkedMultiValueMap<String,String>();body.add("To",to);body.add("Channel",channel);
  try {var data=api.post().uri("/Services/{service}/Verifications",service).headers(h->h.setBasicAuth(account,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(JsonNode.class);String sid=data.path("sid").asText();if(!sid.matches("VE[0-9a-fA-F]{32}"))throw new IllegalStateException();return sid;}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Verification could not be sent. Wait before retrying.");}
 }
 public boolean check(String sid,String code){
  if(configuration!=null)return configured().check(sid,code);
  var body=new LinkedMultiValueMap<String,String>();body.add("VerificationSid",sid);body.add("Code",code);
  try{return "approved".equals(api.post().uri("/Services/{service}/VerificationCheck",service).headers(h->h.setBasicAuth(account,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(JsonNode.class).path("status").asText());}
  catch(HttpClientErrorException e){if(e.getStatusCode().value()==404 || e.getStatusCode().value()==429)return false;throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Verification provider unavailable");}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Verification provider unavailable");}
 }
}
