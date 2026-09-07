package au.com.nakornthai.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {
    @Bean public Clock clock() { return Clock.systemUTC(); }
}
