package au.com.nakornthai.notification.domain;
public interface EmailSender {
 void send(String recipient,String subject,String message);
 default void send(String recipient,String subject,String plain,String html){send(recipient,subject,plain);}
}
