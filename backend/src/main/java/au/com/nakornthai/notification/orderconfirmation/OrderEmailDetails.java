package au.com.nakornthai.notification.orderconfirmation;

import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Renders historical order snapshots; never consults current menu prices. */
public final class OrderEmailDetails {
 private static final DateTimeFormatter LOCAL_TIME=DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a z",Locale.ENGLISH);
 private OrderEmailDetails() {}
 public static String reference(OrderJpaEntity order){return reference(order.getId());}
 public static String reference(UUID id){return id.toString().substring(0,8).toUpperCase(Locale.ROOT);}
 public static String money(long minor,String currency){return currency+" "+BigDecimal.valueOf(minor,2).toPlainString();}
 public static String format(OrderJpaEntity order,ZoneId timezone,boolean staff){
  var body=new StringBuilder("Order details\n");
  body.append("Reference: ").append(reference(order)).append('\n');
  if(order.getCreatedAt()!=null)body.append("Placed: ").append(LOCAL_TIME.withZone(timezone).format(order.getCreatedAt())).append('\n');
  body.append("Customer: ").append(order.getCustomerName()).append('\n');
  if(staff){append(body,"Phone",order.getPhone());append(body,"Email",order.getEmail());}
  append(body,"Fulfilment",label(order.getFulfilment()));
  append(body,"Payment method",label(order.getPaymentMethod()));
  body.append("Payment: ").append(order.getPaidAt()==null?"Not recorded as paid":"Paid").append('\n');
  append(body,"Order status",label(order.getStatus()));
  if(order.getEstimatedReadyAt()!=null&&java.util.Set.of("ACCEPTED","PREPARING","READY").contains(order.getStatus()))
   body.append("Estimated pickup:\n  ").append(LOCAL_TIME.withZone(timezone).format(order.getEstimatedReadyAt())).append('\n');
  if("CANCELLED".equals(order.getStatus())&&order.getCancellationReason()!=null&&!order.getCancellationReason().isBlank())
   body.append("Cancellation reason:\n").append(order.getCancellationReason()).append('\n');
  body.append("\nItems\nUnit prices include selected options.\n");
  for(var item:order.getItems()){
   body.append('\n').append(item.getQuantity()).append(" x ").append(item.getDishName()).append('\n');
   append(body,"  Variation",item.getVariationName());
   body.append("  Unit price: ").append(money(item.getUnitPriceMinor(),order.getCurrency())).append('\n');
   for(var option:item.getSelectedOptions()){
    body.append("  ").append(option.getOptionGroupName()).append(": ").append(option.getOptionName()).append('\n');
    body.append("    Qty per item: ").append(option.getQuantity()).append('\n');
    body.append("    Adjustment each: ").append(money(option.getPriceDeltaMinor(),order.getCurrency())).append('\n');
   }
   body.append("  Line total: ").append(money(Math.multiplyExact(item.getUnitPriceMinor(),item.getQuantity()),order.getCurrency())).append('\n');
  }
  body.append("\nTotal: ").append(money(order.getTotalMinor(),order.getCurrency())).append('\n');
  if(order.getNotes()!=null&&!order.getNotes().isBlank())body.append("\nCustomer notes:\n").append(order.getNotes()).append('\n');
  return body.toString();
 }
 private static String label(String value){
  if(value==null||value.isBlank())return value;
  if("PAYPAL".equals(value))return "PayPal";
  if("PAYID".equals(value))return "PayID";
  String text=value.replace('_',' ').toLowerCase(Locale.ROOT);
  return Character.toUpperCase(text.charAt(0))+text.substring(1);
 }
 private static void append(StringBuilder body,String label,String value){if(value!=null&&!value.isBlank())body.append(label).append(": ").append(value).append('\n');}
}
