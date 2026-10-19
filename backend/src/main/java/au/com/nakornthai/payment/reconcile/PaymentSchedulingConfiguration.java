package au.com.nakornthai.payment.reconcile;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration(proxyBeanMethods=false) @EnableScheduling
public class PaymentSchedulingConfiguration {
 // A second scheduler otherwise suppresses Boot's default and becomes the sole
 // fallback for unrelated @Scheduled work. Keep the conventional default separate.
 @Bean public ThreadPoolTaskScheduler taskScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("scheduled-operations-");return scheduler;}
 @Bean public ThreadPoolTaskScheduler paymentScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("payment-reconciliation-");return scheduler;}
}
