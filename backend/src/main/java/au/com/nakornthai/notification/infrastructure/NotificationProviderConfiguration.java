package au.com.nakornthai.notification.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
public class NotificationProviderConfiguration {
 public NotificationProviderConfiguration(Environment environment) {
  for(String property:Set.of("notification.sms-provider","notification.verification-provider"))
   if(!Set.of("twilio","vonage").contains(environment.getProperty(property,"twilio")))
    throw new IllegalArgumentException(property+" must be twilio or vonage");
 }
}
