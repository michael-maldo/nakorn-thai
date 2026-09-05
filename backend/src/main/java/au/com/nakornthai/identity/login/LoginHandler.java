package au.com.nakornthai.identity.login;
import au.com.nakornthai.identity.infrastructure.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @RequiredArgsConstructor
public class LoginHandler {
    private final JpaUserRepository identity;
    private final Map<String,long[]> attempts=new HashMap<>();
    public JpaUserRepository.Tokens handle(LoginRequest request) {
        long now=System.currentTimeMillis();String name=request.username().toLowerCase(Locale.ROOT);
        synchronized(attempts) {
            attempts.entrySet().removeIf(entry -> now-entry.getValue()[0]>=60000);
            var window=attempts.get(name);
            if(window==null) {
                if(attempts.size()>=4096)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before trying again");
                window=new long[]{now,0};attempts.put(name,window);
            }
            if(++window[1]>10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait one minute before trying again");
        }
        return identity.login(request);
    }
}
