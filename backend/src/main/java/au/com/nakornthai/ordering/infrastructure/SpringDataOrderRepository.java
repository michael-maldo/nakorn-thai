package au.com.nakornthai.ordering.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {
    List<OrderJpaEntity> findByStatusInOrderByCreatedAtAsc(List<String> statuses, Pageable page);
    List<OrderJpaEntity> findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(List<String> statuses, Instant after, Pageable page);
}
