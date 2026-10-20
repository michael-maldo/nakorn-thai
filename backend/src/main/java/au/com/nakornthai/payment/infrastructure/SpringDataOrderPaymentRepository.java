package au.com.nakornthai.payment.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderPaymentRepository extends JpaRepository<OrderPaymentJpaEntity, UUID> {
    List<OrderPaymentJpaEntity> findByOrderIdIn(Collection<UUID> orderIds);
}
