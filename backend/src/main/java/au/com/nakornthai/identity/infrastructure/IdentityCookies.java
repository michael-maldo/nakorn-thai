package au.com.nakornthai.identity.infrastructure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class IdentityCookies {
    public static final String NAME="nakorn_staff_refresh";
    private final boolean secure;
    public IdentityCookies(@Value("${JWT_COOKIE_SECURE:false}") boolean secure) {this.secure=secure;}
    public String issue(String value,Instant expires) {
        return ResponseCookie.from(NAME,value).httpOnly(true).secure(secure).sameSite("Strict").path("/api/identity")
                .maxAge(Duration.between(Instant.now(),expires).isNegative()?Duration.ZERO:Duration.between(Instant.now(),expires)).build().toString();
    }
    public String clear() {return issue("",Instant.EPOCH);}
}
