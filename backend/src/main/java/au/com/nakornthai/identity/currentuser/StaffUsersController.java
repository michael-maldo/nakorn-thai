package au.com.nakornthai.identity.currentuser;
import au.com.nakornthai.identity.infrastructure.*;
import au.com.nakornthai.identity.login.LoginResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/identity/users") @RequiredArgsConstructor
public class StaffUsersController {
    private final SpringDataUserRepository users;
    private final SpringDataStaffSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final EntityManager em;
    public record Create(@NotNull @Pattern(regexp="[a-z0-9][a-z0-9._-]{2,49}") String username,
            @NotNull @Pattern(regexp="ADMIN|FOH|BOH") String role,@NotBlank @Size(min=12,max=72) String password) {}
    public record Update(@NotNull @Min(0) Long version,@NotNull @Pattern(regexp="ADMIN|FOH|BOH") String role,
            boolean enabled,@Size(max=72) String password) {}
    @GetMapping
    ResponseEntity<List<LoginResponse.User>> list() {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.findAll().stream().sorted(Comparator.comparing(UserJpaEntity::getUsername)).map(JpaUserRepository::view).toList());}
    private String encode(String password) {
        if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Passwords require at least 12 characters and at most 72 UTF-8 bytes");
        return passwords.encode(password);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    LoginResponse.User create(@Valid @RequestBody Create request) {
        var user=new UserJpaEntity();user.setUsername(request.username());user.setRole(request.role());user.setPasswordHash(encode(request.password()));
        return JpaUserRepository.view(users.saveAndFlush(user));
    }
    @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void update(@PathVariable UUID id,@Valid @RequestBody Update request) {
        // Serialize admin changes so concurrent requests cannot remove the last admin.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(120012)",Object.class).getSingleResult();
        var user=em.find(UserJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(user==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!user.getVersion().equals(request.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Account changed; reload before saving");
        if(user.isEnabled() && "ADMIN".equals(user.getRole()) && (!request.enabled() || !"ADMIN".equals(request.role())) && users.countByRoleAndEnabledTrue("ADMIN") < 2)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Keep at least one enabled administrator");
        user.setRole(request.role());user.setEnabled(request.enabled());user.setUpdatedAt(Instant.now());
        if(request.password()!=null && !request.password().isBlank())user.setPasswordHash(encode(request.password()));
        sessions.findByUserIdAndRevokedAtIsNull(id).forEach(session -> session.setRevokedAt(Instant.now()));
    }
}
