package au.com.nakornthai.notification.infrastructure.email;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SmtpEmailSenderTest {
 @Test void sendsMultipartAlternativeThroughMockTransport()throws Exception{
  var mail=mock(JavaMailSenderImpl.class);when(mail.getJavaMailProperties()).thenReturn(new Properties());
  var message=new MimeMessage(Session.getInstance(new Properties()));when(mail.createMimeMessage()).thenReturn(message);
  var sender=new SmtpEmailSender("localhost",2525,"","","sender@example.test",true,mail);
  sender.send("guest@example.test","Nakorn Thai — Order #ABCD1234 — Received","Track your order\nhttps://example.test/","<a href=\"https://example.test/\">Track your order</a>");
  verify(mail).send(message);message.saveChanges();assertEquals("Nakorn Thai — Order #ABCD1234 — Received",message.getSubject());
  var alternatives=new ArrayList<String>();collect(message,alternatives);assertEquals(2,alternatives.size());assertTrue(alternatives.get(0).contains("Track your order\nhttps://example.test/"));assertTrue(alternatives.get(1).contains("<a href="));
 }
 void collect(Part part,List<String> found)throws Exception{
  if(part.isMimeType("multipart/*")){var content=(Multipart)part.getContent();if(part.isMimeType("multipart/alternative"))assertEquals(2,content.getCount());for(int i=0;i<content.getCount();i++)collect(content.getBodyPart(i),found);}
  else if(part.isMimeType("text/plain")||part.isMimeType("text/html")){assertTrue(part.getContentType().toLowerCase(Locale.ROOT).contains("utf-8"));found.add((String)part.getContent());}
 }
 @Test void oldPlainTextCallStillUsesSimpleMessage(){
  var mail=mock(JavaMailSenderImpl.class);when(mail.getJavaMailProperties()).thenReturn(new Properties());var sender=new SmtpEmailSender("localhost",2525,"","","sender@example.test",true,mail);
  sender.send("guest@example.test","Legacy subject","Legacy body");verify(mail).send(any(SimpleMailMessage.class));verify(mail,never()).createMimeMessage();
 }
}
