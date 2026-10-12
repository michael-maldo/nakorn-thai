package au.com.nakornthai.notification.orderconfirmation;

import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders historical order snapshots; never consults current menu prices. */
public final class OrderEmailDetails {
 private OrderEmailDetails() {}
 public static String reference(OrderJpaEntity order){return order.getId().toString().substring(0,8).toUpperCase(Locale.ROOT);}
 public static String money(long minor,String currency){return currency+" "+BigDecimal.valueOf(minor,2).toPlainString();}
 public static String format(OrderJpaEntity order,ZoneId timezone,boolean staff){
  var body=new StringBuilder("Order details\n");
  body.append("Reference: ").append(reference(order)).append('\n');
  if(order.getCreatedAt()!=null)body.append("Placed: ").append(DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a z",Locale.ENGLISH).withZone(timezone).format(order.getCreatedAt())).append('\n');
  body.append("Customer: ").append(order.getCustomerName()).append('\n');
  if(staff){append(body,"Phone",order.getPhone());append(body,"Email",order.getEmail());}
  append(body,"Fulfilment",order.getFulfilment());
  append(body,"Payment method",order.getPaymentMethod()==null?null:order.getPaymentMethod().replace('_',' '));
  body.append("Payment: ").append(order.getPaidAt()==null?"Not recorded as paid":"Paid").append('\n');
  append(body,"Order status",order.getStatus());
  body.append("\nItems\n");
  for(var item:order.getItems()){
   body.append(item.getQuantity()).append(" x ").append(item.getDishName()).append('\n');
   append(body,"  Variation",item.getVariationName());
   body.append("  Unit price (including options): ").append(money(item.getUnitPriceMinor(),order.getCurrency())).append('\n');
   for(var option:item.getSelectedOptions())body.append("  ").append(option.getOptionGroupName()).append(": ").append(option.getOptionName()).append(" x ").append(option.getQuantity()).append(" (").append(money(option.getPriceDeltaMinor(),order.getCurrency())).append(" each per item)\n");
   body.append("  Line total: ").append(money(Math.multiplyExact(item.getUnitPriceMinor(),item.getQuantity()),order.getCurrency())).append('\n');
  }
  body.append("\nTotal: ").append(money(order.getTotalMinor(),order.getCurrency())).append('\n');
  if(order.getNotes()!=null&&!order.getNotes().isBlank())body.append("\nCustomer notes:\n").append(order.getNotes()).append('\n');
  return body.toString();
 }
 private static void append(StringBuilder body,String label,String value){if(value!=null&&!value.isBlank())body.append(label).append(": ").append(value).append('\n');}
}
