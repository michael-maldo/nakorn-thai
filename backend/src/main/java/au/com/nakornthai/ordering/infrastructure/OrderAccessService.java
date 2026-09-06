package au.com.nakornthai.ordering.infrastructure;
import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service @RequiredArgsConstructor
public class OrderAccessService {
 private final EntityManager em;
 public void require(OrderJpaEntity order,String token) {
  if(order==null || token==null || !token.matches("[a-f0-9]{64}"))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  String hash=CreateOrderHandler.hash(token);
  boolean original=MessageDigest.isEqual(order.getTrackingHash().getBytes(StandardCharsets.UTF_8),hash.getBytes(StandardCharsets.UTF_8));
  if(!original) {
   Number found=(Number)em.createNativeQuery("SELECT count(*) FROM order_tracking_grant WHERE order_id=:id AND token_hash=:hash AND expires_at>CURRENT_TIMESTAMP",Long.class).setParameter("id",order.getId()).setParameter("hash",hash).getSingleResult();
   if(found.longValue()==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
 }
}
