package au.com.nakornthai.identity.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SpringDataStaffSessionRepository extends JpaRepository<StaffSessionJpaEntity,UUID> {
    List<StaffSessionJpaEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
