package au.com.nakornthai.notification.orderconfirmation;

/** HTML is derived from the same captured plain text, with escaped customer input. */
public final class OrderEmailHtml {
 private OrderEmailHtml(){}
 public static String render(String subject,String plain,String action,String url){
  String suffix=action+"\n"+url+"\n";
  if(!plain.endsWith(suffix))throw new IllegalArgumentException("Tracking action must match the plain-text alternative");
  String content=plain.substring(0,plain.length()-suffix.length()).stripTrailing();
  return "<!doctype html><html lang=\"en\"><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>"+escape(subject)+"</title></head><body style=\"margin:0;background:#faf7f2;color:#242424\"><div role=\"main\" style=\"max-width:600px;margin:0 auto;padding:20px;font-family:Arial,sans-serif;font-size:16px;line-height:1.5\">"+
   "<div style=\"white-space:pre-wrap;overflow-wrap:anywhere\">"+escape(content).replace("\n","<br>")+"</div><p><a href=\""+escape(url)+"\" style=\"display:inline-block;padding:14px 20px;background:#315b42;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold\">"+escape(action)+"</a></p></div></body></html>";
 }
 private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
