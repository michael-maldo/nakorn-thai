package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.restaurant.configuration.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Captured at queue time from trusted configuration, never a request Host header. */
@Component
public class OrderEmailLinks {
 private final RuntimeConfiguration configuration;private final String fallback;private final boolean localAllowed;
 @Autowired public OrderEmailLinks(RuntimeConfiguration configuration,Environment environment){
  this.configuration=configuration;this.fallback=environment.getProperty("nakorn.public-app-url","http://localhost:5173");
  localAllowed=!environment.matchesProfiles("prod")&&java.util.Set.of("dev","test").contains(environment.getProperty("APP_ENV","dev").toLowerCase(java.util.Locale.ROOT));
 }
 /** Fixed development/test fixture constructor. */
 public OrderEmailLinks(String configured){configuration=null;localAllowed=true;fallback=configured;}
 private String base(){String website=configuration==null?"":configuration.snapshot().text("businessWebsiteUrl");
  return BusinessWebsiteUrl.normalize(website.isBlank()?fallback:website,localAllowed);
 }
 public String customer(UUID id){return base()+"#/track-order?order="+id;}
 public String customerToken(String token){if(!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid tracking credential");return base()+"#/track-order?t="+token;}
 public String staff(UUID id){return base()+"#/staff/orders?order="+id;}
}
