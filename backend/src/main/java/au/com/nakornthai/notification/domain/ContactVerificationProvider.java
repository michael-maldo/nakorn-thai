package au.com.nakornthai.notification.domain;
public interface ContactVerificationProvider {
 boolean enabled(String channel);
 String start(String destination, String channel);
 boolean check(String reference, String code);
}
