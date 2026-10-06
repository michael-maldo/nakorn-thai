package au.com.nakornthai.restaurant.configuration;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class ConfigurationHandler {
 private final EntityManager em;private final RuntimeConfiguration runtime;private final CredentialCipher cipher;
 private final IntegrationDiagnostics diagnostics;private final Clock clock;
 private final PlatformTransactionManager transactionManager;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 public record Update(@NotNull @PositiveOrZero Long version,@NotNull Map<String,String> fields,
   Map<String,String> secrets,Set<String> clearSecrets,Boolean acknowledgePendingPayments) {
  @Override public String toString(){return "ConfigurationUpdate[redacted]";}
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Map<String,Object> read(){
  var c=runtime.snapshot();var result=new LinkedHashMap<String,Object>();
  for(String category:List.of("SETTINGS","PAYPAL","PAYID","TWILIO","SMTP"))result.put(category,view(category,c));
  result.put("encryptionAvailable",cipher.available());return result;
 }
 private RestaurantSettingsJpaEntity settings(){return em.find(RestaurantSettingsJpaEntity.class,(short)1,LockModeType.PESSIMISTIC_WRITE);}
 private void category(String category){if(!ConfigurationFields.FIELDS.containsKey(category))throw bad("Unknown configuration category");}
 @Transactional public Map<String,Object> save(String category,Update request,String actor){
  category(category);
  if(category.equals("SETTINGS")&&((request.secrets()!=null&&!request.secrets().isEmpty())||(request.clearSecrets()!=null&&!request.clearSecrets().isEmpty())))throw bad("Business settings do not accept credentials");
  var settings=settings();var previous=runtime.snapshot();
  var row=category.equals("SETTINGS")?null:em.find(IntegrationConfigurationJpaEntity.class,category,LockModeType.PESSIMISTIC_WRITE);
  if(!request.version().equals(row==null?settings.getVersion():row.getVersion()))throw conflict();
  var changed=new TreeSet<String>();
  var fields=new HashMap<>(row==null?settings.getOperationalConfiguration():row.getFields());
  for(var entry:request.fields().entrySet()){
   String key=entry.getKey(),value=entry.getValue();
   if(!ConfigurationFields.FIELDS.get(category).contains(key)||ConfigurationFields.SECRETS.contains(key)||value==null||value.length()>500||value.chars().anyMatch(n->n<32))throw bad("Check configuration fields");
   if(ConfigurationFields.BOOLEAN.contains(key)&&!Set.of("true","false").contains(value))throw bad("Check enabled settings");
   if(key.equals("environment")&&!Set.of("sandbox","live").contains(value))throw bad("Choose sandbox or live");
   if(key.equals("port")){try{int port=Integer.parseInt(value);if(port<1||port>65535)throw new NumberFormatException();}catch(NumberFormatException e){throw bad("Check SMTP port");}}
   if(!Objects.equals(fields.put(key,value.trim()),value.trim()))changed.add(key);
  }
  if(row==null)settings.setOperationalConfiguration(fields);else {
   var secrets=new HashMap<>(row.getSecrets());
   for(var entry:Objects.requireNonNullElse(request.secrets(),Map.<String,String>of()).entrySet()){
    String key=entry.getKey(),value=entry.getValue();
    if(!ConfigurationFields.SECRETS.contains(key)||!ConfigurationFields.FIELDS.get(category).contains(key)||value==null||value.length()>4096)throw bad("Check credential fields");
    // Blank input means preserve, not clear. Explicit clearing suppresses fallback.
    if(!value.isBlank()) {if(!cipher.available())throw bad("A deployment credential master key is required before storing secrets");secrets.put(key,cipher.encrypt(category+"."+key,value));changed.add(key);}
   }
   for(String key:Objects.requireNonNullElse(request.clearSecrets(),Set.<String>of())){
    if(!ConfigurationFields.SECRETS.contains(key)||!ConfigurationFields.FIELDS.get(category).contains(key))throw bad("Check credential fields");
    if(request.secrets()!=null&&request.secrets().containsKey(key)&&!request.secrets().get(key).isBlank())throw bad("Choose replacement or clearing, not both");
    secrets.put(key,"");changed.add(key);
   }
   row.setFields(fields);row.setSecrets(secrets);
   if(!changed.isEmpty())row.setValidationStatus("NOT_TESTED");
   row.setUpdatedAt(clock.instant());row.setUpdatedBy(actor);
  }
  var effective=runtime.snapshot();
  if(category.equals("PAYPAL")&&!changed.isEmpty()){
   var identitySnapshot=effective;
   boolean identityChanged=List.of("environment","clientId","clientSecret").stream().anyMatch(k->!previous.text(k).equals(identitySnapshot.text(k)));
   if(identityChanged&&pending("PAYPAL")>0)throw bad("Resolve pending PayPal orders before changing its environment or credentials");
   // New/replaced PayPal configuration requires deliberate re-enablement.
   var business=new HashMap<>(settings.getOperationalConfiguration());business.put("paypalEnabled","false");settings.setOperationalConfiguration(business);
  }
  if(category.equals("PAYID")&&pending("PAYID")>0&&!Boolean.TRUE.equals(request.acknowledgePendingPayments())&&(!previous.text("identifier").equals(effective.text("identifier"))||!previous.text("accountName").equals(effective.text("accountName"))))throw bad("Pending PayID orders may have previous instructions; acknowledge before changing PayID");
  effective=runtime.snapshot();
  var check=category.equals("SETTINGS")?request.fields():settings.getOperationalConfiguration();
  validateDependencies(check,effective);
  em.flush();audit(category,category+"_CONFIGURATION_UPDATED",changed,actor);
  for(String key:changed)if(ConfigurationFields.BOOLEAN.contains(key)){
   metrics.counter("nakorn.configuration.features","feature",key,"state",effective.flag(key)?"enabled":"disabled").increment();
   if(category.equals("SETTINGS")&&Set.of("paypalEnabled","payidEnabled").contains(key))
    audit("SETTINGS",(key.equals("paypalEnabled")?"PAYPAL":"PAYID")+(effective.flag(key)?"_ENABLED":"_DISABLED"),Set.of(key),actor);
  }
  metrics.counter("nakorn.configuration.updates","category",category,"result","saved").increment();
  log.info("configuration_updated category={} fields={}",category,changed);
  return read();
 }
 private void validateDependencies(Map<String,String> proposed,RuntimeConfiguration.Snapshot c){
  for(var e:proposed.entrySet())if(e.getValue().equals("true"))switch(e.getKey()){
   case "paypalEnabled"->{if(!c.configured("PAYPAL"))throw bad("Configure PayPal before enabling checkout");if(c.text("environment").equals("live")&&c.flag("paypalDashboardManaged")&&!c.flag("paypalValidated"))throw bad("Test live PayPal credentials successfully before enabling checkout");}
   case "payidEnabled"->{if(!c.configured("PAYID"))throw bad("Configure PayID identifier and recipient name first");}
   case "orderPhoneRequired","reservationPhoneRequired"->{if(!ConfigurationFields.verifyConfigured(c))throw bad("Configure the selected SMS verification provider before requiring verified mobiles");}
   case "orderSms","reservationSms"->{if(!ConfigurationFields.smsConfigured(c))throw bad("Configure the selected SMS sender before enabling SMS notifications");}
   case "orderEmail","reservationEmail"->{if(!c.configured("SMTP"))throw bad("Configure SMTP before enabling email notifications");}
   default->{}
  }
  if("true".equals(proposed.get("orderingEnabled"))){
   if(c.flag("orderPhoneRequired")&&!ConfigurationFields.verifyConfigured(c))throw bad("Configure the selected SMS verification provider before accepting online orders");
   if(c.flag("orderSms")&&!ConfigurationFields.smsConfigured(c))throw bad("Configure SMS delivery or disable order SMS updates");
   if(c.flag("orderEmail")&&!c.configured("SMTP"))throw bad("Configure SMTP or disable order email updates");
  }
  if("true".equals(proposed.get("reservationsEnabled"))){
   if(c.flag("reservationPhoneRequired")&&!ConfigurationFields.verifyConfigured(c))throw bad("Configure the selected SMS verification provider before accepting reservation requests");
   if(c.flag("reservationSms")&&!ConfigurationFields.smsConfigured(c))throw bad("Configure SMS delivery or disable reservation SMS updates");
   if(c.flag("reservationEmail")&&!c.configured("SMTP"))throw bad("Configure SMTP or disable reservation email updates");
  }
  if((c.flag("orderSms")&&!c.flag("orderPhoneRequired"))||(c.flag("reservationSms")&&!c.flag("reservationPhoneRequired")))throw bad("SMS updates require verified mobile contacts; disable those SMS updates before disabling verification");
  if(c.flag("orderingEnabled")&&!c.flag("payAtRestaurantEnabled")&&!(c.flag("paypalEnabled")&&c.configured("PAYPAL")&&(!c.text("environment").equals("live")||!c.flag("paypalDashboardManaged")||c.flag("paypalValidated")))&&!(c.flag("payidEnabled")&&c.configured("PAYID")))throw bad("Keep at least one payment method enabled for online ordering");
 }
 private int pending(String method){return ((Number)em.createNativeQuery("SELECT count(*) FROM restaurant_order WHERE payment_method=:method AND paid_at IS NULL AND status NOT IN ('CANCELLED','COMPLETED')",Long.class).setParameter("method",method).getSingleResult()).intValue();}
 private Map<String,Object> view(String category,RuntimeConfiguration.Snapshot c){
  var result=new LinkedHashMap<String,Object>();var fields=new TreeMap<String,String>();var sources=new TreeMap<String,String>();var secretConfigured=new TreeMap<String,Boolean>();
  var row=category.equals("SETTINGS")?null:em.find(IntegrationConfigurationJpaEntity.class,category);
  var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);
  var persisted=row==null?settings.getOperationalConfiguration():row.getFields();
  for(String key:ConfigurationFields.FIELDS.get(category)){
   if(ConfigurationFields.SECRETS.contains(key)){secretConfigured.put(key,!c.text(key).isBlank());sources.put(key,row.getSecrets().containsKey(key)?"DASHBOARD":"ENVIRONMENT_DEFAULT");}
   else {fields.put(key,c.text(key));sources.put(key,persisted.containsKey(key)?"DASHBOARD":"ENVIRONMENT_DEFAULT");}
  }
  boolean twilioVerification=ConfigurationFields.usesVerificationProvider(c,"twilio")&&c.flag("verifySmsEnabled");
  boolean twilioSms=ConfigurationFields.usesSmsProvider(c,"twilio")&&(c.flag("orderSms")||c.flag("reservationSms"));
  boolean configured=c.configured(category),enabled=switch(category){case "PAYPAL"->c.flag("paypalEnabled")&&(!c.text("environment").equals("live")||!c.flag("paypalDashboardManaged")||c.flag("paypalValidated"));case "PAYID"->c.flag("payidEnabled");case "TWILIO"->twilioVerification||twilioSms;case "SMTP"->c.flag("orderEmail")||c.flag("reservationEmail");default->true;};
  boolean ready=!category.equals("TWILIO")||((!twilioVerification||ConfigurationFields.twilioVerifyConfigured(c))&&(!twilioSms||ConfigurationFields.twilioSmsConfigured(c)));
  enabled=enabled&&ready;
  result.put("fields",fields);result.put("sources",sources);result.put("secretsConfigured",secretConfigured);result.put("version",row==null?settings.getVersion():row.getVersion());result.put("configured",configured);result.put("enabled",enabled&&configured);
  result.put("state",row!=null&&(row.getValidationStatus().equals("INVALID")||(configured&&!ready))?"PROBLEM":!configured?"NOT_CONFIGURED":enabled?"ENABLED":"CONFIGURED");
  result.put("validationStatus",row==null?"NOT_TESTED":row.getValidationStatus());
  if(row!=null){result.put("updatedAt",row.getUpdatedAt());result.put("updatedBy",row.getUpdatedBy());result.put("testedAt",row.getTestedAt());}
  if(category.equals("PAYID"))result.put("pendingPayments",pending("PAYID"));
  if(category.equals("TWILIO")){result.put("smsVerificationConfigured",ConfigurationFields.twilioVerifyConfigured(c));result.put("smsSendingConfigured",ConfigurationFields.twilioSmsConfigured(c));result.put("smsSendingValidation","NOT_TESTED");}
  if(category.equals("PAYPAL"))result.put("returnUrl",c.text("returnUrl"));
  return result;
 }
 public Map<String,Object> test(String category,long version,String actor,String recipient){
  category(category);if(category.equals("SETTINGS"))throw bad("Choose an integration to test");
  // Configuration locks are not held across diagnostic network calls.
  var transaction=new TransactionTemplate(transactionManager);
  transaction.executeWithoutResult(status->{var row=em.find(IntegrationConfigurationJpaEntity.class,category);if(row.getVersion()!=version)throw conflict();if(!runtime.snapshot().configured(category))throw bad("Complete the integration configuration first");});
  boolean success;try{if(recipient==null)diagnostics.test(category);else{if(!category.equals("SMTP"))throw bad("Test email requires SMTP");diagnostics.testEmail(recipient);}success=true;}catch(Exception safe){success=false;}
  final boolean valid=success;
  return transaction.execute(status->{settings();var row=em.find(IntegrationConfigurationJpaEntity.class,category,LockModeType.PESSIMISTIC_WRITE);if(row.getVersion()!=version)throw conflict();row.setValidationStatus(valid?"VALID":"INVALID");row.setTestedAt(clock.instant());row.setUpdatedAt(clock.instant());row.setUpdatedBy(actor);em.flush();audit(category,recipient==null?"INTEGRATION_TESTED":"TEST_EMAIL_REQUESTED",Set.of(valid?"SUCCEEDED":"FAILED"),actor);metrics.counter("nakorn.integration.validation","category",category,"result",valid?"valid":"invalid").increment();return Map.<String,Object>of("status",valid?"VALID":"INVALID","message",valid?(category.equals("PAYID")?"Configuration is complete. Bank receipt reconciliation remains manual.":"Connection validation succeeded; delivery capability may require a controlled manual test."):"Validation failed. Check configuration and provider access.","configuration",read());});
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void recordChange(String category,String action,Set<String> fields,String actor){audit(category,action,fields,actor);}
 private void audit(String category,String action,Set<String> fields,String actor){
  em.createNativeQuery("INSERT INTO configuration_audit(id,occurred_at,actor,category,action,changed_fields) VALUES (:id,:at,:actor,:category,:action,cast(:fields AS jsonb))").setParameter("id",UUID.randomUUID()).setParameter("at",clock.instant()).setParameter("actor",actor).setParameter("category",category).setParameter("action",action).setParameter("fields","["+fields.stream().map(k->"\""+k+"\"").collect(java.util.stream.Collectors.joining(","))+"]").executeUpdate();
 }
 @Transactional(readOnly=true) public List<?> audit(){return em.createNativeQuery("SELECT occurred_at,actor,category,action,changed_fields FROM configuration_audit ORDER BY occurred_at DESC LIMIT 100",Object[].class).getResultList().stream().map(raw->{Object[] r=(Object[])raw;return Map.of("timestamp",r[0],"actor",r[1],"category",r[2],"action",r[3],"fields",r[4]);}).toList();}
 private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Configuration changed; refresh before saving or testing");}
}
