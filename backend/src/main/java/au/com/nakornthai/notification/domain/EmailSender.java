package au.com.nakornthai.notification.domain;
public interface EmailSender { void send(String recipient,String subject,String message); }
