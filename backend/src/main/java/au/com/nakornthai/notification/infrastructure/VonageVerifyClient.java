package au.com.nakornthai.notification.infrastructure;

import au.com.nakornthai.notification.domain.ContactVerificationProvider;
import au.com.nakornthai.restaurant.configuration.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import java.util.Set;

@Component @Slf4j
@ConditionalOnProperty(name="notification.verification-provider",havingValue="vonage")
public class VonageVerifyClient implements ContactVerificationProvider {
 private static final String SUCCESS_STATUS="0";
 private static final String CODE_MISMATCH_STATUS="16";
 private static final String ATTEMPT_LIMIT_REACHED_STATUS="17";
 private static final String REQUEST_NOT_FOUND_STATUS="101";
 // Vonage Verify v1: mismatched codes, exhausted attempts and missing requests
 // are normal verification rejections, not provider outages. Other statuses fail closed.
 private static final Set<String> EXPECTED_REJECTION_STATUSES=Set.of(
   CODE_MISMATCH_STATUS,ATTEMPT_LIMIT_REACHED_STATUS,REQUEST_NOT_FOUND_STATUS);
 private final RuntimeConfiguration configuration;
 private final RestClient api;
 @org.springframework.beans.factory.annotation.Autowired
 public VonageVerifyClient(RuntimeConfiguration configuration) {this(configuration,VonageHttpClient.create("https://api.nexmo.com"));}
 public VonageVerifyClient(RuntimeConfiguration configuration,RestClient api) {this.configuration=configuration;this.api=api;}
 private boolean enabled(RuntimeConfiguration.Snapshot c,String channel) {
  return "sms".equals(channel)&&ConfigurationFields.vonageVerifyConfigured(c);
 }
 @Override public boolean enabled(String channel) {return enabled(configuration.snapshot(),channel);}
 @Override public String start(String destination,String channel) {
  var c=configuration.snapshot();
  if(!enabled(c,channel))throw unavailable("Verification channel is unavailable");
  var body=new LinkedMultiValueMap<String,String>();body.add("number",VonageHttpClient.number(destination));body.add("brand",c.text("vonageVerifyBrand"));
  // One SMS only; no voice fallback. Match the application's ten-minute challenge lifetime.
  body.add("workflow_id","6");body.add("code_length","6");body.add("pin_expiry","600");body.add("next_event_wait","600");
  try {
   var result=VonageHttpClient.post(api,"/verify/json",c.text("vonageApiKey"),c.text("vonageApiSecret"),body);
   String reference=result.path("request_id").asText();
   if(!SUCCESS_STATUS.equals(result.path("status").asText())||reference.isBlank()||reference.length()>32)throw new IllegalStateException();
   log.info("notification_provider_result provider=vonage operation=verification_start result=accepted");
   return reference;
  }catch(Exception failure) {
   log.warn("notification_provider_result provider=vonage operation=verification_start result=failed");
   throw unavailable("Verification could not be sent. Wait before retrying.");
  }
 }
 @Override public boolean check(String reference,String code) {
  var c=configuration.snapshot();
  if(!enabled(c,"sms"))throw unavailable("Verification provider unavailable");
  var body=new LinkedMultiValueMap<String,String>();body.add("request_id",reference);body.add("code",code);
  try {
   String status=VonageHttpClient.post(api,"/verify/check/json",c.text("vonageApiKey"),c.text("vonageApiSecret"),body).path("status").asText();
   if(!SUCCESS_STATUS.equals(status)&&!EXPECTED_REJECTION_STATUSES.contains(status))throw new IllegalStateException();
   boolean approved=SUCCESS_STATUS.equals(status);
   log.info("notification_provider_result provider=vonage operation=verification_check result={}",approved?"verified":"rejected");
   return approved;
  }catch(Exception failure) {
   log.warn("notification_provider_result provider=vonage operation=verification_check result=failed");
   throw unavailable("Verification provider unavailable");
  }
 }
 private static ResponseStatusException unavailable(String reason) {return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,reason);}
}
