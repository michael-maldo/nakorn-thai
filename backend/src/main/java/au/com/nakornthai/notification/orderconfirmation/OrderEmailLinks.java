package au.com.nakornthai.notification.orderconfirmation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

/** Uses trusted deployment configuration, never request Host headers. */
@Component
public class OrderEmailLinks {
 private final String base;
 public OrderEmailLinks(@Value("${nakorn.public-app-url}") String configured){
  var uri=URI.create(configured);
  boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()==null?"":uri.getHost());
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||!("https".equals(uri.getScheme())||local&&"http".equals(uri.getScheme()))||uri.getPort()==0||uri.getPort()>65535)
   throw new IllegalArgumentException("Configure an HTTPS public application URL (HTTP is allowed only for loopback development)");
  // The existing PayPal URL may include #/order-confirmation; keep its app base path.
  String raw=configured.split("#",2)[0];base=raw.endsWith("/")?raw:raw+"/";
 }
 public String customer(UUID id){return base+"#/track-order?order="+id;}
 public String staff(UUID id){return base+"#/staff/orders?order="+id;}
}
