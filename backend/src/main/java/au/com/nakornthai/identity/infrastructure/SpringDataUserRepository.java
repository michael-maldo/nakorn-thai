package au.com.nakornthai.identity.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity,UUID> {
    Optional<UserJpaEntity> findByUsername(String username);
    long countByRoleAndEnabledTrue(String role);
}
