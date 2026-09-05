package au.com.nakornthai.reservation.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDateTime;
public interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity,UUID> {
 List<ReservationJpaEntity> findByRequestedAtGreaterThanEqualAndRequestedAtLessThanOrderByRequestedAtAsc(LocalDateTime start, LocalDateTime end);
}
