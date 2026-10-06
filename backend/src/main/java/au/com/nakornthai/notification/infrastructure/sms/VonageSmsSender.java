package au.com.nakornthai.notification.infrastructure.sms;

import au.com.nakornthai.notification.domain.SmsSender;
import au.com.nakornthai.notification.infrastructure.VonageHttpClient;
import au.com.nakornthai.restaurant.configuration.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

@Component @Slf4j
@ConditionalOnProperty(name="notification.sms-provider",havingValue="vonage")
public class VonageSmsSender implements SmsSender {
 private final RuntimeConfiguration configuration;
 private final RestClient api;
 @org.springframework.beans.factory.annotation.Autowired
 public VonageSmsSender(RuntimeConfiguration configuration) {this(configuration,VonageHttpClient.create("https://rest.nexmo.com"));}
 public VonageSmsSender(RuntimeConfiguration configuration,RestClient api) {this.configuration=configuration;this.api=api;}
 @Override public void send(String recipient,String message) {
  var c=configuration.snapshot();
  if(!ConfigurationFields.vonageSmsConfigured(c))throw new IllegalStateException("SMS delivery unavailable");
  var body=new LinkedMultiValueMap<String,String>();
  body.add("from",VonageHttpClient.number(c.text("vonageSmsFrom")));body.add("to",VonageHttpClient.number(recipient));body.add("text",message);body.add("type","unicode");
  try {
   var response=VonageHttpClient.post(api,"/sms/json",c.text("vonageApiKey"),c.text("vonageApiSecret"),body);
   var messages=response.path("messages");
   if(!messages.isArray()||messages.isEmpty())throw new IllegalStateException();
   for(var result:messages)if(!"0".equals(result.path("status").asText()))throw new IllegalStateException();
   log.info("notification_provider_result provider=vonage operation=sms_send result=accepted");
  }catch(Exception failure) {
   log.warn("notification_provider_result provider=vonage operation=sms_send result=failed");
   throw new IllegalStateException("SMS delivery unavailable");
  }
 }
}
