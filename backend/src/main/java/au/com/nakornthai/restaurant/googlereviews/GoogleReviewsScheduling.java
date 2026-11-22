package au.com.nakornthai.restaurant.googlereviews;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class GoogleReviewsScheduling {
    @Bean
    ThreadPoolTaskScheduler googleReviewsScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("google-reviews-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
