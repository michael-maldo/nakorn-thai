package au.com.nakornthai.restaurant.infrastructure;
import au.com.nakornthai.restaurant.configuration.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import java.util.*;
@Service @RequiredArgsConstructor
public class DatabaseRuntimeConfiguration implements RuntimeConfiguration,ApplicationRunner {
 private final EntityManager em;private final Environment environment;private final CredentialCipher cipher;
 // MVCC keeps standalone multi-query reads consistent without blocking configuration writers.
 // When joining a business/write transaction, retain that caller's isolation and locks.
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Snapshot snapshot(){
  var values=new HashMap<String,String>();ConfigurationFields.ENV.forEach((k,v)->values.put(k,environment.getProperty(v,ConfigurationFields.defaultValue(k))));
  values.put("returnUrl",environment.getProperty("PAYPAL_RETURN_URL","http://localhost:5173/#/order-confirmation"));
  var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);
  if(settings!=null)values.putAll(settings.getOperationalConfiguration());
  for(var row:em.createQuery("from IntegrationConfigurationJpaEntity",IntegrationConfigurationJpaEntity.class).getResultList()){
   values.putAll(row.getFields());row.getSecrets().forEach((k,v)->values.put(k,v.isEmpty()?"":cipher.decrypt(row.getCategory()+"."+k,v)));
   if(row.getCategory().equals("PAYPAL")){values.put("paypalDashboardManaged",String.valueOf(!row.getFields().isEmpty()||!row.getSecrets().isEmpty()));values.put("paypalValidated",String.valueOf(row.getValidationStatus().equals("VALID")));}
  }
  return new Snapshot(values);
 }
 @Override @Transactional(readOnly=true) public void run(ApplicationArguments args){
  // Never fall back to another provider identity when persisted secrets are unreadable.
  for(var row:em.createQuery("from IntegrationConfigurationJpaEntity",IntegrationConfigurationJpaEntity.class).getResultList())row.getSecrets().forEach((k,v)->{if(!v.isEmpty())cipher.decrypt(row.getCategory()+"."+k,v);});
 }
}
