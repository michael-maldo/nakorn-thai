package au.com.nakornthai.notification;

import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.notification.infrastructure.sms.*;
import au.com.nakornthai.restaurant.configuration.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotificationProviderSelectionTest {
 ApplicationContextRunner context=new ApplicationContextRunner().withBean(RuntimeConfiguration.class,()->()->new RuntimeConfiguration.Snapshot(Map.of())).withUserConfiguration(NotificationProviderConfiguration.class,TwilioSmsSender.class,VonageSmsSender.class,TwilioVerifyClient.class,VonageVerifyClient.class,TwilioConnectionDiagnostics.class,au.com.nakornthai.restaurant.infrastructure.ProviderIntegrationDiagnostics.class).withBean(au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider.class,()->org.mockito.Mockito.mock(au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider.class)).withBean(au.com.nakornthai.notification.infrastructure.email.SmtpEmailSender.class,()->org.mockito.Mockito.mock(au.com.nakornthai.notification.infrastructure.email.SmtpEmailSender.class));
 @Test void defaultsToTwilio() {selection(null,null,TwilioSmsSender.class,TwilioVerifyClient.class);}
 @Test void twilioSelections() {selection("twilio","twilio",TwilioSmsSender.class,TwilioVerifyClient.class);}
 @Test void vonageSelections() {selection("vonage","vonage",VonageSmsSender.class,VonageVerifyClient.class);}
 @Test void vonageSmsTwilioVerification() {selection("vonage","twilio",VonageSmsSender.class,TwilioVerifyClient.class);}
 @Test void twilioSmsVonageVerification() {selection("twilio","vonage",TwilioSmsSender.class,VonageVerifyClient.class);}
 @Test void unsupportedVerificationSelectionFailsStartup() {context.withPropertyValues("notification.verification-provider=invalid").run(c->assertNotNull(c.getStartupFailure()));}
 @Test void unsupportedSelectionFailsStartup() {context.withPropertyValues("notification.sms-provider=invalid").run(c->assertNotNull(c.getStartupFailure()));}
 void selection(String sms,String verify,Class<?> smsType,Class<?> verifyType) {
  var runner=sms==null?context:context.withPropertyValues("notification.sms-provider="+sms,"notification.verification-provider="+verify);
  runner.run(c->{assertNull(c.getStartupFailure());assertNotNull(c.getBean(TwilioConnectionDiagnostics.class));assertNotNull(c.getBean(au.com.nakornthai.restaurant.configuration.IntegrationDiagnostics.class));assertEquals(1,c.getBeansOfType(SmsSender.class).size());assertEquals(1,c.getBeansOfType(ContactVerificationProvider.class).size());assertInstanceOf(smsType,c.getBean(SmsSender.class));assertInstanceOf(verifyType,c.getBean(ContactVerificationProvider.class));});
 }
 @Test void readinessUsesSelectionsIndependently() {
  var values=new java.util.HashMap<String,String>(Map.of("smsProvider","vonage","verificationProvider","twilio","vonageApiKey","test-key","vonageApiSecret","test-secret","vonageSmsFrom","TestSender","accountSid","AC"+"a".repeat(32),"authToken","test-token","verifyServiceSid","VA"+"b".repeat(32),"verifySmsEnabled","true"));
  assertTrue(ConfigurationFields.smsConfigured(new RuntimeConfiguration.Snapshot(values)));assertTrue(ConfigurationFields.verifyConfigured(new RuntimeConfiguration.Snapshot(values)));
  values.put("smsProvider","twilio");values.put("smsFrom","+61412345678");values.put("verificationProvider","vonage");values.put("vonageVerifyBrand","TestBrand");values.put("vonageVerifySmsEnabled","true");
  assertTrue(ConfigurationFields.smsConfigured(new RuntimeConfiguration.Snapshot(values)));assertTrue(ConfigurationFields.verifyConfigured(new RuntimeConfiguration.Snapshot(values)));
 }
 @Test void twilioCapabilitiesDoNotDependOnSelectedProviders() {
  var values=new java.util.HashMap<String,String>(Map.of("smsProvider","vonage","verificationProvider","vonage","accountSid","AC"+"a".repeat(32),"authToken","test-token","verifySmsEnabled","true"));
  var c=new RuntimeConfiguration.Snapshot(values);
  assertFalse(ConfigurationFields.twilioVerifyConfigured(c));assertFalse(ConfigurationFields.twilioSmsConfigured(c));
  values.put("verifyServiceSid","VA"+"b".repeat(32));values.put("smsFrom","+61412345678");
  c=new RuntimeConfiguration.Snapshot(values);assertTrue(ConfigurationFields.twilioVerifyConfigured(c));assertTrue(ConfigurationFields.twilioSmsConfigured(c));
 }
}
