package au.com.nakornthai.restaurant.infrastructure;
import au.com.nakornthai.restaurant.configuration.IntegrationDiagnostics;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import au.com.nakornthai.notification.infrastructure.TwilioConnectionDiagnostics;
import au.com.nakornthai.notification.infrastructure.email.SmtpEmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ProviderIntegrationDiagnostics implements IntegrationDiagnostics {
 private final PayPalPaymentProvider paypal;private final TwilioConnectionDiagnostics twilio;private final SmtpEmailSender email;
 public void test(String category){switch(category){case "PAYPAL"->paypal.testConnection();case "TWILIO"->twilio.testConnection();case "SMTP"->email.testConnection();case "PAYID"->{}default->throw new IllegalArgumentException("Unknown integration");}}
 public void testEmail(String recipient){email.send(recipient,"Nakorn Thai integration test","This test email was explicitly requested by a Nakorn Thai administrator.");}
}
