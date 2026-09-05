package au.com.nakornthai.reservation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;

public interface SpringDataFunctionEnquiryRepository extends JpaRepository<FunctionEnquiryJpaEntity,UUID> {
    Page<FunctionEnquiryJpaEntity> findByStatus(String status, Pageable pageable);
}
