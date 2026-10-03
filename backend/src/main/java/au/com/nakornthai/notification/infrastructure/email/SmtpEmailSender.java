package au.com.nakornthai.notification.infrastructure.email;
import au.com.nakornthai.notification.domain.EmailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
@Component
public class SmtpEmailSender implements EmailSender {
 private final JavaMailSenderImpl mail=new JavaMailSenderImpl();
 private final String host,from;
 public SmtpEmailSender(@Value("${SMTP_HOST:}") String host,@Value("${SMTP_PORT:587}") int port,@Value("${SMTP_USERNAME:}") String username,@Value("${SMTP_PASSWORD:}") String password,@Value("${SMTP_FROM:}") String from,@Value("${SMTP_STARTTLS:true}") boolean tls) {
  this.host=host;this.from=from;mail.setHost(host);mail.setPort(port);mail.setUsername(username);mail.setPassword(password);
  var p=mail.getJavaMailProperties();p.setProperty("mail.smtp.auth",String.valueOf(!username.isBlank()));p.setProperty("mail.smtp.starttls.enable",String.valueOf(tls));p.setProperty("mail.smtp.starttls.required",String.valueOf(tls));
  p.setProperty("mail.smtp.connectiontimeout","5000");p.setProperty("mail.smtp.timeout","10000");p.setProperty("mail.smtp.writetimeout","10000");
 }
 public void send(String recipient,String subject,String body) {
  if(host.isBlank() || from.isBlank())throw new IllegalStateException("Email delivery unavailable");
  var message=new SimpleMailMessage();message.setFrom(from);message.setTo(recipient);message.setSubject(subject);message.setText(body);
  try {mail.send(message);}catch(Exception e){throw new IllegalStateException("Email delivery unavailable");}
 }
}
