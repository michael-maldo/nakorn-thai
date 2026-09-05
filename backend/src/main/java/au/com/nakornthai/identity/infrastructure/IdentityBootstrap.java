package au.com.nakornthai.identity.infrastructure;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
@Component @RequiredArgsConstructor
public class IdentityBootstrap implements ApplicationRunner {
    private final SpringDataUserRepository users;
    private final Environment env;
    @Override @Transactional public void run(ApplicationArguments arguments) {
        seed("MENU_ADMIN","admin","ADMIN");seed("FOH","foh","FOH");seed("BOH","kitchen","BOH");
    }
    private void seed(String prefix,String fallback,String role) {
        String hash=env.getProperty(prefix+"_PASSWORD_HASH","");
        if(hash.isBlank())return;
        String username=env.getProperty(prefix+"_USERNAME",fallback).toLowerCase(Locale.ROOT);
        if(!username.matches("[a-z0-9][a-z0-9._-]{2,49}") || !hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("Invalid bootstrap staff username or bcrypt hash");
        if(users.findByUsername(username).isPresent())return; // Never reset staff-managed credentials/roles.
        var user=new UserJpaEntity();user.setUsername(username);user.setPasswordHash(hash);user.setRole(role);users.save(user);
    }
}
