package au.com.nakornthai.ordering.trackorder;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.notification.orderconfirmation.OrderEmailDetails;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.List;
@Service
public class TrackOrderHandler {
 private final EntityManager em;private final OrderViewTokens tokens;private final String timezone;
 public TrackOrderHandler(EntityManager em,OrderViewTokens tokens,@Value("${nakorn.restaurant-time-zone:Australia/Melbourne}") String timezone){this.em=em;this.tokens=tokens;this.timezone=timezone;}
 public record Option(String groupName,String name,int quantity,long priceDeltaMinor){}
 public record Item(String name,String variation,int quantity,long unitPriceMinor,long lineTotalMinor,List<Option> options){}
 public record View(String reference,String status,Instant estimatedReadyAt,Instant updatedAt,String timezone,String fulfilment,long totalMinor,String currency,String customerInstructions,List<Item> items){}
 @Transactional(readOnly=true) public View handle(String token){
  var o=em.find(OrderJpaEntity.class,tokens.require(token));
  if(o==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"This order tracking link has expired or is invalid.");
  var items=o.getItems().stream().map(i->new Item(i.getDishName(),i.getVariationName(),i.getQuantity(),i.getUnitPriceMinor(),Math.multiplyExact(i.getUnitPriceMinor(),i.getQuantity()),i.getSelectedOptions().stream().map(p->new Option(p.getOptionGroupName(),p.getOptionName(),p.getQuantity(),p.getPriceDeltaMinor())).toList())).toList();
  return new View(OrderEmailDetails.reference(o.getId()),o.getStatus(),o.getEstimatedReadyAt(),o.getUpdatedAt(),timezone,o.getFulfilment(),o.getTotalMinor(),o.getCurrency(),o.getNotes(),items);
 }
}
