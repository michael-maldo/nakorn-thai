package au.com.nakornthai.notification.orderconfirmation;

import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryJpaEntity;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.restaurant.infrastructure.RestaurantSettingsJpaEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="PUBLIC_APP_URL=https://restaurant.example.test/") @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class OrderEmailPresentationIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv("DB_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
 }
 @Autowired EntityManager em;@Autowired JdbcTemplate jdbc;@Autowired SendOrderConfirmationHandler handler;
 @MockitoBean EmailSender email;@MockitoBean SmsSender sms;
 @MockitoBean au.com.nakornthai.restaurant.configuration.IntegrationDiagnostics diagnostics;
 @MockitoBean(name="taskScheduler") org.springframework.scheduling.TaskScheduler scheduler;
 @MockitoBean Clock clock;
 static final UUID ID=UUID.fromString("abcd1234-0000-4000-8000-000000000001");
 static final Instant PLACED=Instant.parse("2030-10-07T07:05:00Z"),PICKUP=Instant.parse("2030-10-07T07:45:00Z");
 static final ZoneId ZONE=ZoneId.of("Australia/Melbourne");
 final List<UUID> variations=new ArrayList<>(),options=new ArrayList<>();
 @BeforeEach void fixture(){
  when(clock.instant()).thenReturn(PLACED);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);var fields=new HashMap<>(settings.getOperationalConfiguration());
  fields.put("orderEmail","true");fields.put("staffOrderEmail","true");fields.put("orderSms","false");fields.put("staffOrderEmailAddress","staff@example.test");settings.setOperationalConfiguration(fields);
  var order=new OrderJpaEntity();order.setId(ID);order.setTrackingHash("a".repeat(64));order.setRequestHash("b".repeat(64));order.setCustomerName("Alex Sample");order.setPhone("+61400000000");order.setEmail("alex.sample@example.test");order.setNotes("Please pack sauces separately. No cutlery, thank you.");order.setPaymentMethod("PAYPAL");order.setCreatedAt(PLACED);order.setUpdatedAt(PLACED);order.setTotalMinor(9920);
  var curry=item(order,"Green Curry","Chicken",2,2390,2640);option(curry,"Extras","Extra vegetables",1,250);option(curry,"Heat","Mild",1,0);
  var noodles=item(order,"Pad Thai","Prawns",1,2690,3290);option(noodles,"Extras","Extra prawns",2,300);
  item(order,"Jasmine Rice","Large",3,450,450);
  em.persist(order);em.flush();em.clear();
  // Current menu names/prices deliberately diverge after the historical snapshots.
  for(UUID variation:variations){jdbc.update("UPDATE menu_item_variation SET name='Current variation',price_minor=99999 WHERE id=?",variation);jdbc.update("UPDATE menu_item SET name='Current dish' WHERE id=(SELECT menu_item_id FROM menu_item_variation WHERE id=?)",variation);}
  for(UUID option:options){jdbc.update("UPDATE menu_option SET name='Current option' WHERE id=?",option);jdbc.update("UPDATE menu_item_option_price SET price_delta_minor=88888 WHERE option_id=?",option);}
 }
 OrderItemJpaEntity item(OrderJpaEntity order,String name,String variation,int quantity,long base,long unit){
  UUID category=UUID.randomUUID(),dish=UUID.randomUUID(),id=UUID.randomUUID();variations.add(id);
  jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'Sample',?)",category,"sample-"+category);
  jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,?,?, 'Sample','PUBLISHED')",dish,category,name,"sample-"+dish);
  jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,?,?,true)",id,dish,variation,base);
  var item=new OrderItemJpaEntity();item.setOrder(order);item.setVariationId(id);item.setDishName(name);item.setVariationName(variation);item.setQuantity(quantity);item.setCollectionId(UUID.randomUUID());item.setCollectionName("Dinner menu");item.setCollectionSlug("dinner");item.setVariationBasePriceMinor(base);item.setUnitPriceMinor(unit);order.getItems().add(item);return item;
 }
 void option(OrderItemJpaEntity item,String groupName,String name,int quantity,long delta){
  UUID group=UUID.randomUUID(),id=UUID.randomUUID();options.add(id);
  jdbc.update("INSERT INTO menu_option_group(id,code,name,selection_type) VALUES (?,?,?,'MULTIPLE')",group,"sample-"+group,groupName);
  jdbc.update("INSERT INTO menu_option(id,option_group_id,code,name) VALUES (?,?,?,?)",id,group,"sample-"+id,name);
  UUID dish=jdbc.queryForObject("SELECT menu_item_id FROM menu_item_variation WHERE id=?",UUID.class,item.getVariationId());
  jdbc.update("INSERT INTO menu_item_option_group(menu_item_id,option_group_id,max_selections) VALUES (?,?,20)",dish,group);
  jdbc.update("INSERT INTO menu_item_option_price(menu_item_id,option_group_id,option_id,price_delta_minor) VALUES (?,?,?,?)",dish,group,id,delta);
  var option=new OrderItemOptionJpaEntity();option.setOrderItem(item);option.setOptionId(id);option.setOptionGroupName(groupName);option.setOptionName(name);option.setQuantity(quantity);option.setPriceDeltaMinor(delta);option.setCreatedAt(PLACED);item.getSelectedOptions().add(option);
 }
 void notify(NotificationType type){
  var order=em.find(OrderJpaEntity.class,ID);
  handler.handle(new SendOrderConfirmationCommand(ID,type,null,order.getEmail(),order.getCustomerName(),order.getTotalMinor(),order.getEstimatedReadyAt(),ZONE));em.flush();
 }
 List<NotificationDeliveryJpaEntity> jobs(){return em.createQuery("from NotificationDeliveryJpaEntity where orderId=:id",NotificationDeliveryJpaEntity.class).setParameter("id",ID).getResultList();}
 @Test void generatesRealQueuedEmailsFromReloadedSnapshots()throws Exception{
  notify(NotificationType.ORDER_RECEIVED);
  var order=em.find(OrderJpaEntity.class,ID);order.setStatus("ACCEPTED");order.setPaidAt(PLACED.plusSeconds(300));order.setEstimatedReadyAt(PICKUP);notify(NotificationType.ORDER_ACCEPTED);
  order.setStatus("READY");notify(NotificationType.ORDER_READY);
  order.setStatus("CANCELLED");order.setCancellationReason("The restaurant cannot fulfil this order.");notify(NotificationType.ORDER_CANCELLED);
  assertEquals(5,jobs().size());
  assertEquals(9920,order.getItems().stream().mapToLong(i->i.getUnitPriceMinor()*i.getQuantity()).sum());
  for(var item:order.getItems())assertEquals(item.getUnitPriceMinor(),item.getVariationBasePriceMinor()+item.getSelectedOptions().stream().mapToLong(o->o.getPriceDeltaMinor()*o.getQuantity()).sum());
  for(var job:jobs()){
   String body=job.getBody();
   for(String expected:List.of("ABCD1234","Alex Sample","2 x Green Curry","Chicken","1 x Pad Thai","Prawns","3 x Jasmine Rice","Large","Extra vegetables","Mild","Extra prawns","AUD 26.40","AUD 32.90","AUD 4.50","AUD 52.80","AUD 32.90","AUD 13.50","Total: AUD 99.20","Please pack sauces separately."))assertTrue(body.contains(expected),expected+" missing from "+job.getType());
   for(String secret:List.of("a".repeat(64),"b".repeat(64),"Current dish","Current variation","Current option","999.99","888.88"))assertFalse(body.contains(secret));
   assertEquals(job.getType()==NotificationType.STAFF_ORDER_RECEIVED,body.contains("alex.sample@example.test"));
   assertEquals(job.getType()==NotificationType.STAFF_ORDER_RECEIVED,body.contains("Phone: +61400000000"));
   assertTrue(body.contains("Placed: 7 Oct 2030, 6:05 PM AEDT"));assertTrue(body.contains("Payment method: PayPal"));assertTrue(body.contains("Fulfilment: Pickup"));
   assertTrue(body.contains("Unit prices include selected options."));assertTrue(body.contains("    Qty per item: 2\n    Adjustment each: AUD 3.00"));assertTrue(body.contains("Adjustment each: AUD 0.00"));
   switch(job.getType()){
    case ORDER_RECEIVED->{assertTrue(body.contains("not been accepted yet"));assertTrue(body.contains("Payment: Not recorded as paid"));assertFalse(body.contains("Estimated pickup:"));}
    case ORDER_ACCEPTED->{assertTrue(body.contains("Your order has been accepted."));assertTrue(body.contains("Estimated pickup:\n  7 Oct 2030, 6:45 PM AEDT"));assertTrue(body.contains("Payment: Paid"));}
    case ORDER_READY->{assertTrue(body.contains("233 Glenferrie Rd, Malvern"));assertTrue(body.contains("Order status: Ready"));}
    case ORDER_CANCELLED->{assertTrue(body.contains("cancellation does not automatically refund payment"));assertTrue(body.contains("Cancellation reason:\nThe restaurant cannot fulfil this order."));assertFalse(body.contains("Estimated pickup:"));}
    case STAFF_ORDER_RECEIVED->{assertTrue(body.contains("A new customer order needs review."));assertFalse(body.contains("your order"));assertTrue(body.contains("Payment: Not recorded as paid"));}
    default->fail("Unexpected sample type");
   }
   String action=job.getType()==NotificationType.STAFF_ORDER_RECEIVED?"View order":"Track your order";
   String link="https://restaurant.example.test/#/"+(job.getType()==NotificationType.STAFF_ORDER_RECEIVED?"staff/orders":"track-order")+"?order="+ID;
   assertTrue(body.endsWith(action+"\n"+link+"\n"));assertTrue(job.getSubject().contains("#ABCD1234"));
   assertEquals(OrderEmailHtml.render(job.getSubject(),body,action,link),job.getHtmlBody());
   assertTrue(job.getHtmlBody().contains("href=\""+link+"\""));
   String directory=System.getProperty("order.email.samples.directory");
   if(directory!=null){
    var path=Path.of(directory);Files.createDirectories(path);
    String filename=job.getType()==NotificationType.STAFF_ORDER_RECEIVED?"staff-order-received.txt":"customer-"+job.getType().name().toLowerCase(Locale.ROOT).replace('_','-')+".txt";
    Files.writeString(path.resolve(filename),"Subject: "+job.getSubject()+"\n\n"+body);
    Files.writeString(path.resolve(filename.replace(".txt",".html")),job.getHtmlBody());
   }
  }
  verifyNoInteractions(email,sms,diagnostics);
 }
 @Test void missingOptionalFieldsDoNotLeakNullOrInventPickup(){
  var order=em.find(OrderJpaEntity.class,ID);order.setEmail(null);order.setNotes("");order.setStatus("ACCEPTED");
  notify(NotificationType.ORDER_ACCEPTED);assertTrue(jobs().isEmpty());
  order.setStatus("NEW");notify(NotificationType.ORDER_RECEIVED);assertEquals(1,jobs().size());var body=jobs().getFirst().getBody();
  assertFalse(body.contains("null"));assertFalse(body.contains("Email:"));assertFalse(body.contains("Customer notes:"));assertFalse(body.contains("Estimated pickup:"));verifyNoInteractions(email,sms,diagnostics);
 }
}
