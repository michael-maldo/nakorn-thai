package au.com.nakornthai.notification.domain;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class ContactDestination {
 private ContactDestination() {}
 public static String normalize(String channel,String value) {
  if(value==null || value.isBlank())return null;
  String v=value.trim();
  if("SMS".equals(channel)) {
   if(!v.matches("[+0-9 ()-]{6,30}"))throw invalid();
   v=v.replaceAll("[ ()-]","");
   if(v.matches("0[2378][0-9]{8}|04[0-9]{8}"))v="+61"+v.substring(1);
   if(!v.matches("\\+[1-9][0-9]{7,14}") || (v.startsWith("+61") && !v.matches("\\+61[23478][0-9]{8}")))throw invalid();
   return v;
  }
  if(!"EMAIL".equals(channel))throw invalid();
  if(v.length()>254 || !v.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid();
  int at=v.lastIndexOf('@');return v.substring(0,at)+v.substring(at).toLowerCase(Locale.ROOT);
 }
 public static String normalizeMobile(String value) {
  String mobile=normalize("SMS",value);
  if(mobile!=null && mobile.startsWith("+61") && !mobile.matches("\\+614[0-9]{8}"))throw invalid();
  return mobile;
 }
 public static String hash(String value) {
  try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Check contact destination and channel");}
}
