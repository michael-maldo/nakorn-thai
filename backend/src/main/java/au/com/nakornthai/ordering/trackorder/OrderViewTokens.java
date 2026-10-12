package au.com.nakornthai.ordering.trackorder;
import au.com.nakornthai.restaurant.configuration.CredentialCipher;
import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
@Service
public class OrderViewTokens {
 private final JdbcTemplate jdbc;private final CredentialCipher cipher;private final Clock clock;
 private final SecureRandom random=new SecureRandom();
 public OrderViewTokens(JdbcTemplate jdbc,CredentialCipher cipher,Clock clock){this.jdbc=jdbc;this.cipher=cipher;this.clock=clock;}
 /** Issued only during trusted customer email enqueueing. Locks also serialize cross-event issuance. */
 @Transactional(propagation=Propagation.MANDATORY)
 public String forEmail(UUID orderId){
  jdbc.queryForList("SELECT id FROM restaurant_order WHERE id=? FOR UPDATE",orderId);
  var rows=jdbc.queryForList("SELECT encrypted_token,expires_at,revoked_at FROM order_view_token WHERE order_id=?",orderId);
  if(!rows.isEmpty()){
   var r=rows.getFirst();if(r.get("revoked_at")!=null||!((java.sql.Timestamp)r.get("expires_at")).toInstant().isAfter(clock.instant()))return null;
   return cipher.decrypt("order-view:"+orderId,(String)r.get("encrypted_token"));
  }
  if(!cipher.available())return null; // Preserve verified recovery on deployments without encryption configured.
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);var issued=clock.instant();
  jdbc.update("INSERT INTO order_view_token(order_id,token_hash,encrypted_token,issued_at,expires_at) VALUES (?,?,?,?,?)",orderId,CreateOrderHandler.hash(token),cipher.encrypt("order-view:"+orderId,token),java.sql.Timestamp.from(issued),java.sql.Timestamp.from(issued.plus(Duration.ofDays(7))));
  return token;
 }
 @Transactional(readOnly=true)
 public UUID require(String token){
  if(token==null||!token.matches("[0-9a-f]{64}"))throw unavailable();
  // Hash lookup scopes access to exactly one order; there is no caller-supplied order selector.
  var ids=jdbc.query("SELECT order_id FROM order_view_token WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?",(rs,n)->rs.getObject(1,UUID.class),CreateOrderHandler.hash(token),java.sql.Timestamp.from(clock.instant()));
  if(ids.size()!=1)throw unavailable();return ids.getFirst();
 }
 @Transactional public void revoke(UUID id){jdbc.update("UPDATE order_view_token SET revoked_at=GREATEST(issued_at,?) WHERE order_id=? AND revoked_at IS NULL",java.sql.Timestamp.from(clock.instant()),id);}
 private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"This order tracking link has expired or is invalid. Please contact Nakorn Thai and provide your order reference if you need assistance.");}
}
