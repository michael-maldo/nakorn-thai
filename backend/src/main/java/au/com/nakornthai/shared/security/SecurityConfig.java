package au.com.nakornthai.shared.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu/collections/*/items",
                                "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/staff/menu/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, error) -> response.setStatus(401)))
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                // Keep CSRF protection for browser-authenticated staff writes.
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${MENU_ADMIN_USERNAME:admin}") String username,
            @Value("${MENU_ADMIN_PASSWORD_HASH:}") String passwordHash) {
        if (passwordHash.isBlank()) return new InMemoryUserDetailsManager();
        if (!passwordHash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("MENU_ADMIN_PASSWORD_HASH must be a bcrypt hash");
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + passwordHash).roles("ADMIN").build());
    }
}
