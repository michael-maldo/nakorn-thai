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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu/collections/*/items",
                                "/media/menu/*", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/options", "/api/orders/csrf", "/api/orders/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservations").permitAll()
                        .requestMatchers("/api/staff/reservations", "/api/staff/reservations/**").hasAnyRole("ADMIN", "FOH")
                        .requestMatchers(HttpMethod.GET, "/api/identity/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/identity/login", "/api/identity/refresh", "/api/identity/logout").permitAll()
                        .requestMatchers("/api/identity/users", "/api/identity/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/identity/me").authenticated()
                        .requestMatchers("/api/staff/foh/**").hasAnyRole("ADMIN", "FOH")
                        .requestMatchers("/api/staff/kitchen/**").hasAnyRole("ADMIN", "BOH")
                        .requestMatchers("/api/staff/orders/**").hasAnyRole("ADMIN", "FOH", "BOH")
                        .requestMatchers("/api/staff/menu/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                // Keep CSRF protection for browser-authenticated staff writes.
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() { return new InMemoryUserDetailsManager(); }
    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
    }
    @Bean
    JwtService jwtService(@Value("${JWT_SECRET_BASE64:}") String secret, org.springframework.core.env.Environment environment) {
        return new JwtService(secret, environment.matchesProfiles("prod"));
    }
    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt, au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions) {
        return new JwtAuthenticationFilter(jwt, sessions);
    }
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);return registration;
    }
}
