package au.com.nakornthai.notification.reservationconfirmation;

import au.com.nakornthai.restaurant.configuration.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Trusted website configuration only; the staff route always requires sign-in. */
@Component
public class ReservationEmailLinks {
 private final RuntimeConfiguration configuration;
 private final String fallback;
 private final boolean localAllowed;
 public ReservationEmailLinks(RuntimeConfiguration configuration,Environment environment) {
  this.configuration=configuration;
  fallback=environment.getProperty("nakorn.public-app-url","http://localhost:5173");
  localAllowed=!environment.matchesProfiles("prod")&&java.util.Set.of("dev","test").contains(environment.getProperty("APP_ENV","dev").toLowerCase(java.util.Locale.ROOT));
 }
 public String staff() {
  String website=configuration.snapshot().text("businessWebsiteUrl");
  return BusinessWebsiteUrl.normalize(website.isBlank()?fallback:website,localAllowed)+"#/staff/reservations";
 }
}
