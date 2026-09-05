package au.com.nakornthai.shared.security;
import au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SpringDataStaffSessionRepository sessions;
    public JwtAuthenticationFilter(JwtService jwt, SpringDataStaffSessionRepository sessions) { this.jwt=jwt;this.sessions=sessions; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if (header!=null && header.startsWith("Bearer ")) {
            try {
                var claims=jwt.verify(header.substring(7));
                var session=sessions.findById(UUID.fromString(claims.get("sid",String.class))).orElseThrow(() -> new io.jsonwebtoken.JwtException("Session missing"));
                var user=session.getUser();
                if (session.getRevokedAt()!=null || !session.getExpiresAt().isAfter(Instant.now()) || !user.isEnabled()
                        || !user.getId().toString().equals(claims.getSubject())) throw new io.jsonwebtoken.JwtException("Session expired");
                // Roles and account status come from the database, not client claims.
                var auth=UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext(); response.setStatus(401); return;
            }
        }
        chain.doFilter(request,response);
    }
}
