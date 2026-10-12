package au.com.nakornthai.restaurant.configuration;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
/** Administrator/deployment supplied base URLs only; never accepts request headers or redirects. */
public final class BusinessWebsiteUrl {
 private BusinessWebsiteUrl(){}
 public static String normalize(String value,boolean localAllowed){
  try {
   var u=URI.create(value.trim());String host=u.getHost();
   boolean local=host!=null&&Set.of("localhost","127.0.0.1","[::1]").contains(host.toLowerCase(Locale.ROOT));
   if(host==null||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535||
      !("https".equalsIgnoreCase(u.getScheme())||localAllowed&&local&&"http".equalsIgnoreCase(u.getScheme()))||
      !u.normalize().equals(u)||u.getRawPath().contains("%")||u.getRawPath().contains("//"))throw new IllegalArgumentException();
   String path=u.getPath();if(!path.endsWith("/"))path+="/";
   return new URI(u.getScheme().toLowerCase(Locale.ROOT),null,host.toLowerCase(Locale.ROOT),u.getPort(),path,null,null).toASCIIString();
  }catch(Exception e){throw new IllegalArgumentException("Use an HTTPS business website URL without credentials, query or fragment; HTTP loopback is allowed only in development/test");}
 }
}
