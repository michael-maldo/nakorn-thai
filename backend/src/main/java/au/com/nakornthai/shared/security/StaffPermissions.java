package au.com.nakornthai.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** Shared policy for status-update writes, including generic cancellation payloads. */
public final class StaffPermissions {
    private StaffPermissions() {}
    private static boolean role(Authentication actor, String role) {
        return actor != null && actor.isAuthenticated() && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
    public static void requireOrderTransition(Authentication actor, String status) {
        boolean allowed = role(actor, "ADMIN") || (!"CANCELLED".equals(status) &&
                (role(actor, "FOH") || role(actor, "BOH")));
        require(allowed);
    }
    public static void requireReservationTransition(Authentication actor, String status) {
        require(role(actor, "ADMIN") || role(actor, "FOH") && !"CANCELLED".equals(status));
    }
    private static void require(boolean allowed) {
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
