package au.com.nakornthai.ordering.infrastructure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="restaurant_order_event") @Getter @Setter
public class OrderEventJpaEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID orderId;
    @Column(nullable=false, length=20) private String status;
    @Column(nullable=false, length=100) private String actor;
    @Column(nullable=false) private Instant createdAt;
}
