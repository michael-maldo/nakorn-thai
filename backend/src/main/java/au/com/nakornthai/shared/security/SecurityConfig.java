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
                                "/media/menu/*", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/options", "/api/orders/csrf", "/api/orders/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers("/api/staff/foh/**").hasAnyRole("ADMIN", "FOH")
                        .requestMatchers("/api/staff/kitchen/**").hasAnyRole("ADMIN", "BOH")
                        .requestMatchers("/api/staff/orders/**").hasAnyRole("ADMIN", "FOH", "BOH")
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
            @Value("${MENU_ADMIN_PASSWORD_HASH:}") String passwordHash,
            @Value("${FOH_USERNAME:foh}") String fohUsername,
            @Value("${FOH_PASSWORD_HASH:}") String fohHash,
            @Value("${BOH_USERNAME:kitchen}") String bohUsername,
            @Value("${BOH_PASSWORD_HASH:}") String bohHash) {
        var users = new InMemoryUserDetailsManager();
        addAccount(users, username, passwordHash, "ADMIN");
        addAccount(users, fohUsername, fohHash, "FOH");
        addAccount(users, bohUsername, bohHash, "BOH");
        return users;
    }

    private void addAccount(InMemoryUserDetailsManager users, String username, String hash, String role) {
        if (hash.isBlank()) return;
        if (username.isBlank() || username.length() > 100 || users.userExists(username))
            throw new IllegalArgumentException("Staff usernames must be nonempty and distinct");
        if (!hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException(role + " password hash must be bcrypt");
        users.createUser(User.withUsername(username).password("{bcrypt}" + hash).roles(role).build());
    }
}
