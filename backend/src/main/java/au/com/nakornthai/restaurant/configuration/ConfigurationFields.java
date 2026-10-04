package au.com.nakornthai.restaurant.configuration;
import java.util.*;
public final class ConfigurationFields {
 private ConfigurationFields(){}
 public static final Map<String,String> ENV=Map.ofEntries(
 Map.entry("orderingEnabled","ONLINE_ORDERING_ENABLED"),Map.entry("reservationsEnabled","ONLINE_RESERVATIONS_ENABLED"),
 Map.entry("orderPhoneRequired","ORDER_PHONE_VERIFICATION_REQUIRED"),Map.entry("reservationPhoneRequired","RESERVATION_PHONE_VERIFICATION_REQUIRED"),
 Map.entry("orderSms","ORDER_SMS_NOTIFICATIONS_ENABLED"),Map.entry("orderEmail","ORDER_EMAIL_NOTIFICATIONS_ENABLED"),Map.entry("reservationSms","RESERVATION_SMS_NOTIFICATIONS_ENABLED"),Map.entry("reservationEmail","RESERVATION_EMAIL_NOTIFICATIONS_ENABLED"),
 Map.entry("paypalEnabled","PAYPAL_ENABLED"),Map.entry("payidEnabled","PAYID_ENABLED"),Map.entry("payAtRestaurantEnabled","PAY_AT_RESTAURANT_ENABLED"),
 Map.entry("environment","PAYPAL_ENV"),Map.entry("clientId","PAYPAL_CLIENT_ID"),Map.entry("clientSecret","PAYPAL_CLIENT_SECRET"),
 Map.entry("identifier","PAYID_IDENTIFIER"),Map.entry("accountName","PAYID_ACCOUNT_NAME"),
 Map.entry("accountSid","TWILIO_ACCOUNT_SID"),Map.entry("authToken","TWILIO_AUTH_TOKEN"),Map.entry("verifyServiceSid","TWILIO_VERIFY_SERVICE_SID"),Map.entry("smsFrom","TWILIO_SMS_FROM"),Map.entry("verifySmsEnabled","VERIFY_SMS_ENABLED"),Map.entry("verifyEmailEnabled","VERIFY_EMAIL_ENABLED"),
 Map.entry("host","SMTP_HOST"),Map.entry("port","SMTP_PORT"),Map.entry("username","SMTP_USERNAME"),Map.entry("password","SMTP_PASSWORD"),Map.entry("from","SMTP_FROM"),Map.entry("starttls","SMTP_STARTTLS"));
 public static final Map<String,Set<String>> FIELDS=Map.of(
 "SETTINGS",Set.of("orderingEnabled","reservationsEnabled","orderPhoneRequired","reservationPhoneRequired","orderSms","orderEmail","reservationSms","reservationEmail","paypalEnabled","payidEnabled","payAtRestaurantEnabled"),
 "PAYPAL",Set.of("environment","clientId","clientSecret"),"PAYID",Set.of("identifier","accountName"),
 "TWILIO",Set.of("accountSid","authToken","verifyServiceSid","smsFrom","verifySmsEnabled","verifyEmailEnabled"),"SMTP",Set.of("host","port","username","password","from","starttls"));
 public static final Set<String> SECRETS=Set.of("clientSecret","authToken","password");
 public static final Set<String> BOOLEAN=new HashSet<>(FIELDS.get("SETTINGS"));
 static {BOOLEAN.addAll(Set.of("verifySmsEnabled","verifyEmailEnabled","starttls"));}
 public static String defaultValue(String key){return switch(key){case "environment"->"sandbox";case "port"->"587";case "orderingEnabled","paypalEnabled","payidEnabled","verifySmsEnabled","verifyEmailEnabled"->"false";default->BOOLEAN.contains(key)?"true":"";};}
 public static boolean configured(String category,RuntimeConfiguration.Snapshot c){return switch(category){
 case "PAYPAL" -> Set.of("sandbox","live").contains(c.text("environment"))&&!c.text("clientId").isBlank()&&!c.text("clientSecret").isBlank()&&validCallback(c.text("returnUrl"),c.text("environment"));
 case "PAYID" -> !c.text("identifier").isBlank()&&!c.text("accountName").isBlank();
 case "TWILIO" -> c.text("accountSid").matches("AC[0-9a-fA-F]{32}")&&!c.text("authToken").isBlank();
 case "SMTP" -> c.text("host").matches("[A-Za-z0-9.-]{1,253}")&&c.text("from").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")&&validPort(c.text("port"))&&(c.text("username").isBlank()||!c.text("password").isBlank());
 default -> true;};}
 public static boolean verifyConfigured(RuntimeConfiguration.Snapshot c){return c.configured("TWILIO")&&c.text("verifyServiceSid").matches("VA[0-9a-fA-F]{32}")&&c.flag("verifySmsEnabled");}
 public static boolean smsConfigured(RuntimeConfiguration.Snapshot c){return c.configured("TWILIO")&&c.text("smsFrom").matches("\\+[1-9][0-9]{7,14}");}
 private static boolean validPort(String p){try{int n=Integer.parseInt(p);return n>0&&n<=65535;}catch(Exception e){return false;}}
 public static boolean validCallback(String value,String environment){try{var u=java.net.URI.create(value);return u.getHost()!=null&&u.getUserInfo()==null&&"/order-confirmation".equals(u.getFragment())&&(environment.equals("live")?"https".equals(u.getScheme()):Set.of("http","https").contains(u.getScheme()));}catch(Exception e){return false;}}
}
