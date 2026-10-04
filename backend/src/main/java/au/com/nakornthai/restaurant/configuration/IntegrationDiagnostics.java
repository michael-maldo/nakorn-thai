package au.com.nakornthai.restaurant.configuration;
/** Provider diagnostics port; implementations must not create payment or OTP work. */
public interface IntegrationDiagnostics {
 void test(String category);
 void testEmail(String recipient);
}
