package au.com.nakornthai.notification.reservationconfirmation;

/** Same captured text as the plain alternative; no customer tracking actions. */
public final class ReservationEmailHtml {
 private ReservationEmailHtml() {}
 public static String render(String subject,String plain,String staffUrl){
  String content=plain;
  if(staffUrl!=null){
   String suffix="View reservations\n"+staffUrl+"\n";
   if(!plain.endsWith(suffix))throw new IllegalArgumentException("Staff action must match plain text");
   content=plain.substring(0,plain.length()-suffix.length()).stripTrailing();
  }
  String action=staffUrl==null?"":"<p><a href=\""+escape(staffUrl)+"\" style=\"display:inline-block;padding:14px 20px;background:#315b42;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold\">View reservations</a></p>";
  return "<!doctype html><html lang=\"en-AU\"><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>"+escape(subject)+"</title></head><body style=\"margin:0;background:#faf7f2;color:#242424\"><div role=\"main\" style=\"max-width:600px;margin:0 auto;padding:20px;font-family:Arial,sans-serif;font-size:16px;line-height:1.5\"><div style=\"white-space:pre-wrap;overflow-wrap:anywhere\">"+escape(content).replace("\n","<br>")+"</div>"+action+"</div></body></html>";
 }
 private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
